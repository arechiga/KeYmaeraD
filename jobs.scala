package DLBanyan

import scala.actors.Actor
import scala.actors.AbstractActor
import scala.actors.Actor._
import scala.actors.Futures._

import scala.actors.remote.RemoteActor
import scala.actors.remote.RemoteActor._
import scala.actors.remote._


import Procedures._

object Jobs {
  type JobID = Nodes.NodeID

/*
  private var nextID = 0
  def nextJobID : JobID = {
    val res = nextID;
    nextID += 1;
    res
  }
*/


  class SyncMap[A,B] 
    extends scala.collection.mutable.HashMap[A, B]
    with scala.collection.mutable.SynchronizedMap[A,B]
    

  case class JobData( jid: JobID,
                      s: scala.actors.OutputChannel[Any],
                      p: String, // the proc
                      sq: Sequent, 
                      t: Long // start time
                   )

  class JobMaster(myPort: Int) extends Actor {

    // the jobs we've sent out to workers
    val jobs =
      new scala.collection.mutable.HashMap[JobID, 
                                           (JobData, scala.actors.OutputChannel[Any])]()

    /* Keep a queue of unstarted jobs and idling workers.
     * At each loop, match them up and dispatch.
     */

    val newjobs = new scala.collection.mutable.Queue[JobData]()
    val idleworkers =
      new scala.collection.mutable.Queue[scala.actors.OutputChannel[Any]]()

//    val localworker = new JobWorker()
//    localworker.start()

    def act(): Unit = {
      println("jobmaster acting")

      RemoteActor.classLoader = getClass().getClassLoader()

      alive(myPort)
      register('master, self)

      while(true){

        // Assign jobs if we can.
        while((! idleworkers.isEmpty) && (! newjobs.isEmpty) ){
          println("looking...")
          val iw = idleworkers.dequeue()
          val jd@JobData(jid, s, p, sq, t) = newjobs.dequeue()
          iw ! ( ('job, p, sq, jid) )
          jobs.put(jid, (jd, iw))
          ()
        };

//        println("jobmaster listening")
        receive {
          case 'quit =>
            println("jobmaster quitting, notifying workers")
//            localworker !? 'quit
            for (w <- idleworkers) {
              println("notifying worker: " + w)
              w ! ('quit)
            }
            println("jobmaster quits")
            sender ! ()
            exit

          case ('idling) =>
            println("A worker idles.")
            idleworkers.enqueue(sender)

          case ('job, p: String, sq: Sequent, jid: JobID) =>
//            val jid = nextJobID
            val t = System.currentTimeMillis
            // PERF: insert easy filter here.
            newjobs += JobData(jid, sender, p, sq, t)

         case 'list =>
           println(jobs)

         case ('jobdone, jid: JobID, res: Sequent) =>
           jobs.get(jid) match {
             case Some((JobData(_,s,p,sq,t),w)) =>
               s ! ('jobdone, jid, res)
             case None =>
               throw new Error("invalid jobID")
           }
           jobs.remove(jid)

         case ('jobdone, jid: JobID) =>
           jobs.get(jid) match {
             case Some((JobData(_,s,p,sq,t),w)) =>
               s ! ('jobdone, jid)
             case None =>
               throw new Error("invalid jobID")
           }
           jobs.remove(jid)

         case ('abort, jid: JobID) =>
          val njs = newjobs.dequeueAll( {case JobData(x,_,_,_,_) => x == jid})
          println("jobmaster: cancelling "+ njs.length +   " job(s) ")
          for(JobData(jid1,s,p,sq,t) <- njs){
            s ! ('jobdone, jid1) 
          }
          jobs.get(jid) match {
            case Some((JobData(_,s,p,sq,t), w)) =>
              println("jobmaster: aborting job " + jid)
              w ! 'abort
            case None =>
              if (njs.length == 0)
                println("jobmaster: could not find job " + jid)
          }

          case msg =>
            println ("jobserver got message: " + msg)

        }
      }
    }
  }



  class JobWorker(masterNode: Node) extends Actor {

    case class JobData(proc:String, 
                       sq:Sequent,
                       jid: JobID,
                       sender:scala.actors.OutputChannel[Any])

    var working: Option[Procedure]  = None

    val procqueue = 
      new scala.collection.mutable.Queue[JobData]()

    val lock = new Object()

    def tryworking(master: AbstractActor) : Unit = {
      if (working == None  && ! procqueue.isEmpty) {
        master ! ("procqueue has length " + procqueue.length)
        println("procqueue has length " + procqueue.length)
        val jd@JobData(p,sq,jid,sender) = procqueue.dequeue
        procs.get(p) match {
          case Some(pr) =>
            val sf = self
            if(pr.applies(sq)) {
              master ! ("working on jid " + jid)
              println("working on jid " + jid)
              lock.synchronized{
                working = Some(pr)
              } 
              future {
                master ! "about to proceed"
                val res = pr.proceed(sq)
                println("proceeded")
                master ! "did proceed"
                res match {
                  case Some(r) =>
                    sf ! ('done, jd, r)
                    println("sent back result")
                  case None =>
                    sf ! ('done, jd)
                    println("sent back abort message")
                }
              }
            }  else  sf ! ('doesnotapply, jid) // should not happen
                

          case None =>
            // should not happen
        }
      }
    }
    
    private def abort() = {
        lock.synchronized{ working } match {
          case Some(pr) =>
            pr.abort
          case None => // XXX need to look through the queue
            println("got abort when nothing was running")
        }
    }


    def act(): Unit = {
      println("jobworker acting")

      val master = select(masterNode, 'master)
      trapExit = true
      link(master)

      master ! ('idling)

      while(true){
        tryworking(master)
        receive {
          case 'quit =>
            println("jobmaster quitting, worker aborts jobs")
            abort()
            println("jobmaster quitting, worker exits")
            exit

          case ('job, p: String, sq: Sequent, jid: JobID) =>
           procqueue.enqueue(JobData(p, sq, jid, sender))
           master ! "enqueued a job"

         case ('done, JobData(p,sq,jid,jobsender), res: Sequent) =>
           println("got result")
           lock.synchronized {working = None}
           println("about to send notification")
           jobsender ! ('jobdone, jid, res)
           println("about to send idling message")
           master ! ('idling)
          
         case ('done, JobData(p,sq,jid,jobsender)) =>
           lock.synchronized  { working = None }
           jobsender ! ('jobdone, jid)
           master ! ('idling)
         
         case 'abort => 
           abort()
        }
      }
    }
  }
}

// Code entry point for workers.
 object WorkerMain {
   import Jobs._
  
    import org.apache.commons.cli.Options
    import org.apache.commons.cli.CommandLine
    import java.net.InetAddress

    def parse(args: Array[String]) : CommandLine = {

      //use CLI to parse command line options
      var opts = new org.apache.commons.cli.Options();
      opts.addOption("c", true, "coordinator address (default = localhost)");
      opts.addOption("cp", true, "coordinator port (default = 50001)");

      //do parsing
      var parser = new org.apache.commons.cli.GnuParser();
      parser.parse(opts, args);

    }

    def main(args: Array[String]) : Unit = {
      println("worker says: hello world.")

      val cmd = parse(args)
      
      //process options
      //coordinator address and port
      if (!cmd.hasOption("c")) {
        println("Using default coordinator address localhost. (use -c to specify).")
      }

      val coorHost = 
        new Node(cmd.getOptionValue("c","localhost"), 
                 java.lang.Integer.parseInt(cmd.getOptionValue("cp", "50001")))
      
      println("coordinator at " + coorHost.toString)
      
      val jobWorker = new JobWorker(coorHost)
      jobWorker.start
      ()
    }

  }

