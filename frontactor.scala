
package KeYmaeraD
import scala.actors.Actor
import scala.actors.Actor._
import java.io.InputStream
import java.io.FileOutputStream
import java.io.File


import Nodes._


object TreeActions {

  import RulesUtil._
  import Procedures._

  val myAddr = java.net.InetAddress.getLocalHost().getHostAddress()
  var myPort = 0
  try {
    val testSocket = new java.net.ServerSocket(0)
    myPort = testSocket.getLocalPort()
    testSocket.close()
  } catch {
    case ioe: java.io.IOException =>
      println("using a random port")
    myPort = 50000 + scala.util.Random.nextInt(10000)
  }

  println("job master will listen on port " + myPort)

  val jobs = new scala.collection.mutable.HashMap[NodeID, Long]()
  val jobmaster = new Jobs.JobMaster(myPort)
  jobmaster.start

  var hereNode: ProofNode = nullNode


  var treemodel: Option[KeYmaeraD.GUI.TreeModel] = None

  def getnodethen(ndID: NodeID, f: (ProofNode) => Unit ): Unit = 
    nodeTable.get(ndID) match {
      case Some(nd) => f(nd)
      case None =>
        println ("node " + ndID + " does not exist.")
    }

  def gotonode(nd: ProofNode) : Unit = {
        hereNode = nd
//        println("now at node " + nd.nodeID )
    }

  def shownode(nd: ProofNode) : Unit = 
        println(nd.toPrettyString)

/*
  def showhints(nd: ProofNode, pos: Position): Unit = {
    val fm = lookup(pos, nd.goal)
    fm match {
      case ...
    }
  }
  
*/

  def attachnodes(pt: ProofNode, newnds: List[ProofNode]): Unit = {
    for (newnd <- newnds){
      newnd.setParent(Some(pt.nodeID))
      register(newnd)
      pt.addchild(newnd.nodeID)
    }
//    println("treemodel attaching nodes: " + newnds)
    treemodel.map(_.fireNodesInserted(pt, newnds)) // GUI
//    treemodel.map(_.fireChanged(pt)) // GUI
  }

  def attachnode(pt: ProofNode, newnd: ProofNode): Unit = {
    attachnodes(pt,List(newnd))
  }

  def applyrule(hn: OrNode, 
                p: Position, 
                rl: ProofRule): Option[List[NodeID]] = try {
    val res =     rl(p)(hn.goal) 
    res match {
      case Some((Nil, _)) | Some((List(Sequent(_,Nil,List(True))),_)) => //proved
        val pnd = new DoneNode(rl.toString, hn.goal)
        attachnode(hn,pnd)
        propagateProvedUp(hn.nodeID, pnd.nodeID)
        Some(Nil)
      case Some((List(sq), _)) => 
        val ornd = new OrNode(rl.toString, sq)
        attachnode(hn,ornd)
        Some(List(ornd.nodeID))
      case Some( (sqs, fvs)  ) =>
        val andnd = new AndNode(rl.toString, hn.goal, Nil)
        attachnode(hn,andnd)
        val subname = rl.toString + " subgoal"
        val ornds = sqs.map(s => new OrNode(subname, s))
        attachnodes(andnd, ornds) 
        Some(ornds.map(_.nodeID))
      case None => 
        None
    }
  } catch {
    case (e:RulesUtil.LookupError) => 
      println("index out of range : " + p)
      None
  }

  // Returns true if successfully submitted. 
  def submitproc(ornd: OrNode, proc: String): Boolean
  = procs.get(proc) match {
    case None => false
    case Some(pr) => 
      if(pr.applies(ornd.goal)) {
        val wknd = new WorkingNode(proc,ornd.goal)
        attachnode(ornd, wknd)

        jobmaster ! (('job, proc, ornd.goal, wknd.nodeID))
        val t = System.currentTimeMillis
        jobs.put(wknd.nodeID, t)
        true
      } else {
        false
      }
  }
    

/* crawl the tree to update statuses.
 * propagateProvedUp is called on nd when a child of nd is proved.
 */
  def propagateProvedUp(ndID: NodeID, from: NodeID): Unit = {
    val nd = getnode(ndID)
    nd match {
      case AndNode(r,g,svs) =>
        val others = nd.getChildren.filterNot( _ ==  from)
        val os = others.map(getnode).map(_.getStatus)
        os.find( _ != Proved) match {
           case None =>
             nd.setStatus(Proved)
             treemodel.map(_.fireChanged(nd)) // GUI
             nd.getParent match {
               case Some(p) =>
                 propagateProvedUp(p, ndID)
               case None =>
             }    
           case Some(_) =>
        }

      case OrNode(r,g) =>
        nd.setStatus(Proved)
        treemodel.map(_.fireChanged(nd)) // GUI
        val others = nd.getChildren.filterNot( _ ==  from)
        others.map(x => propagateIrrelevantDown(x))
        nd.getParent match {
          case Some(p) =>
            propagateProvedUp(p, ndID)
          case None =>
            
        }
      case DoneNode(r,g) =>
        // shouldn't ever happen
        throw new Error("bad call of propagateProvedUp")
    }
  }

  // called when ndID becomes irrelevant.
  def propagateIrrelevantDown(ndID: NodeID) : Unit = {
    val nd = getnode(ndID)
    nd.setStatus(Irrelevant(nd.getStatus))
    treemodel.map(_.fireChanged(nd)) //GUI
    jobs.get(ndID) match {
      case Some(t) => 
        jobs.remove(ndID)
        jobmaster ! ('abort, ndID)
      case None =>
        
    }
    nd.getChildren.map( propagateIrrelevantDown)

  }
}


object BlockingActorScheduler {
  lazy val scheduler = {
    val s = new scala.actors.scheduler.ResizableThreadPoolScheduler(false)
    s.start()
    s
  }
}


trait BlockingActor extends Actor {
  override def scheduler = BlockingActorScheduler.scheduler
}

// WorkerTracers have two purposes. The first is to record the ouput
// of worker subprocesses, which do not have their own terminal. The
// second is to ensure that these subprocesses get scheduled by
// demanding input from them. TODO: understand why that, without these
// tracers, workers can sometimes make no progress.
class WorkerTracer(id: Int, ins: InputStream) extends BlockingActor {

  def act(): Unit = {
    try
    {
      val f = new File("worker" + id + ".out");
      val out = new FileOutputStream(f);
      val buf =new Array[Byte](1024);
      var len = ins.read(buf)
      while (len > 0) {
        out.write(buf,0,len)
        len = ins.read(buf)
      }
      out.close();
      ins.close();
      println("created trace in " + f)
    }
    catch { case e => println("caught while tracing: " + e) }

  }
}


class FrontActor extends Actor {

  import TreeActions._  
  import RulesUtil._

  import Tactics.Tactic

//  val scriptRunner = scala.tools.nsc.ScriptRunner
  

  def act(): Unit = {
    println("acting")
    link(jobmaster)

    while(true){
      receive {
        case 'quit =>
          println("frontactor quitting")
          for((jid,t) <- jobs){
            jobmaster ! ('abort, jid)
          }
          jobmaster !? 'quit
          sender ! ()
        // TODO: It would be better to just |exit|,
        // but how then to kill the REPL frontend?
        // HACK:
        // If we're going to System.exit here,
        // we don't want the worker 'quit messages to
        // get cut off. So sleep to allow them to 
        // work through the pipes.
          Thread.sleep(500)
          System.exit(0) 
          exit
        case 'gui => 
          val fe = KeYmaeraD.GUI.FE.start(self)
          sender ! ()
        case ('registergui, tm: KeYmaeraD.GUI.TreeModel) => 
          treemodel = Some(tm)
        case ('findworkers, number:Int) =>   
          var i = 1
          while (i <= number) {
	    println("starting worker " + i)
	    val pb = new ProcessBuilder("./runworker",
                                        "-cp",
                                        myPort.toString)
            val p = pb.start()
            val wt = new WorkerTracer(i, p.getInputStream())
            link(wt)
            wt.start()
	    i += 1
          } 
          println("started workers")
          sender ! ()
        case 'here =>
          displayThisNode
          sender ! ()
        case ('load, filename:String) =>
          loadfile(filename)
          sender ! ()
        case ('loadex, filename:String) =>
          loadfile("examples/" + filename)
          sender ! ()
        case ('show, nd: NodeID) =>
          getnodethen(nd, shownode _)
          sender ! ()
        case ('goto, nd: NodeID) =>
          getnodethen(nd, gotonode _)
          getnodethen(nd, shownode _)
          sender ! ()
        case 'gotoroot =>
          gotonode(rootNode)
          shownode(rootNode)
          sender ! ()
        case ('rule, pos: Position, rl: ProofRule) =>
          hereNode  match {
            case ornd@OrNode(_,_) =>
              val r = applyrule(ornd,pos,rl)
              r match {
                case Some(_) => 
                   println("success")
                case None => 
                  println("rule cannot be applied there")    
              }

            case _ => 
              println("cannot apply rule here")
          }
          sender ! ()

        case ('tactic, tct: Tactic) =>
          hereNode  match {
            case ornd@OrNode(_,_) =>
              tct(ornd)
            case _ => 
              println("cannot apply rule here")
          }
          sender ! ()


        case ('job, proc: String) => 
          hereNode match {
            case ornd@OrNode(r,sq) =>
              val res = submitproc(ornd, proc)
              if(res) ()
              else println("procedure " + proc + " does not apply here.")
            case _ =>
              println("can only do a procedure on a ornode")
              
          }
          sender ! ()

        case ('jobdone, ndID: NodeID, sq : Sequent) =>
          (jobs.get(ndID), nodeTable.get(ndID), sq) match {
            case (None, _, _ ) =>
              ()
            // Proved.
            case (Some(t), Some(nd), Sequent(_, Nil, List(True))) =>
              jobs.remove(ndID)
              nd.getParent match {
                case Some(ptid) =>
                  val pt = getnode(ptid)
                  val nd1 = DoneNode("quantifier elimination", sq)
                  attachnode(pt, nd1)
                  propagateProvedUp(pt.nodeID, nd1.nodeID)
                case None =>
                  throw new Error("no parent")
              }
            // Disproved.
            case (Some(t), Some(nd), Sequent(_, Nil, Nil)) =>
              jobs.remove(ndID)
              nd.setStatus(Disproved)
              treemodel.map(_.fireChanged(nd)) //GUI
            // Nothing to report
            case (Some(t), Some(nd), _) => 
              throw new Error("proc should return True or False")
            case (Some(t), None, _) => 
              throw new Error("node not in nodeTable")
          }

        // Aborted job.
        case ('jobdone, ndID : NodeID) =>
          nodeTable.get(ndID) match {
            case None =>
              ()
            case Some(nd) =>
              jobs.remove(ndID)
              nd.setStatus(Aborted)
              treemodel.map(_.fireChanged(nd)) //GUI
          }


        case 'jobs =>
          println(jobs.toList)
          sender ! ()
        
        case ('abort, jb: NodeID) =>
          println("aborting job")
          jobmaster ! ('abort, jb)
          sender ! ()

        case ('abortall) => 
          for((jid,t) <- jobs){
            jobmaster ! ('abort, jid)
          }
        sender ! ()

        case msg =>
          println("got message: " + msg)
          sender ! ()
      }
    }
    
  }


  def displayThisNode : Unit = {
    shownode(hereNode)
  }


//  def parseformula(fms : String) : Formula

  def loadfile(filename: String) : Unit = try {
    // kill pending jobs.
    for( (ndID, t) <- jobs) {
      jobmaster ! ('abort, ndID)
    }
    jobs.clear

    val fi = 
      new java.io.FileInputStream(filename)

    val dlp = new DLParser(fi)

    dlp.result match {
      case Some(g) =>
        val nd = new OrNode("loaded from " + filename, g)
        register(nd)
        hereNode = nd
        rootNode = nd 
        treemodel.map(_.fireNewRoot(nd))// GUI
      case None =>
        println("failed to parse file " + filename)
        //@TODO Display an error. Notify the GUI of the error, which should display the message

    }

    ()

  } catch {
    case e => 
      println("failed to load file " + filename)
      println("due to " + e)
  }
  //@TODO finally {fi.close}
}



