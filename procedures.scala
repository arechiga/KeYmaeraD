package DLBanyan

object Procedures {
  import Prover._




  val procs = new scala.collection.mutable.HashMap[String,Procedure]()
  procs ++= List(("ch", CohenHormander),
                 ("math", Mathematica))



  def canQE_Term(sig:Map[String,(List[Sort],Sort)]) : Term =>  Boolean 
   = tm => tm match {
    case Fn(f, args) if List("+","-", "*", "/", "^").contains(f) =>
      args.forall(canQE_Term(sig))
    case Fn(f, arg::args) => false
    case Fn(f, Nil) =>
      sig.get(f) match {
        case Some((_, Real)) => true
        case None => true // assume that it's real-valued
        case _ => false
      }
    case _ => true
  }

  // Indicate whether, e.g.,  we can apply substitution safely. 
  def canQE(fm: Formula, sig : Map[String,(List[Sort],Sort)]): Boolean 
   = fm match {
    case True | False => true
    case Atom(R(r,ps)) if List("=","<", ">", ">=", "<=").contains(r) => 
      ps.forall(canQE_Term(sig))
    case Not(f) => canQE(f,sig)
    case Binop(_,f1,f2) => 
      canQE(f1,sig) && canQE(f2,sig)
    case Quantifier(_,Real,v,f) =>
      canQE(f,sig)
    case _ => false
  }



// for now, these things only close or disprove a goal.

  @serializable
  abstract class Procedure {
    def applies(sq: Sequent): Boolean

    def proceed(sq: Sequent, tm: Long): Option[Sequent]

    def proceed(sq: Sequent): Option[Sequent]

    def abort: Unit
  }


 


  object CohenHormander extends Procedure {

    def applies(sq: Sequent) : Boolean = sq match {
      case Sequent(sig, c,s) =>
        !(c.exists(x => ! canQE(x,sig)) ||  s.exists(x => ! canQE(x,sig)) )
    }


    def proceed(sq: Sequent): Option[Sequent] = sq match {
      case Sequent(sig, c,s) => 
//       println("about to attempt quantifier elimination on:\n")
//       P.print_Formula(fm)
      val fm = Binop(Imp,AM.list_conj(c), AM.list_disj(s));
//      val fm1 = AM.univ_close(fm);
      val fm1 = AM.makeQEable(fm);
       try{ 
 
        CV.start()
//         val r =  AM.real_elim_goal(fm)
         val r = AM.real_elim(fm1)
         if(r == True) {
           //println("success!")
           Some(Sequent(sig, Nil,List(True)))
         } else {
           // TODO this doesn't actually mean disproved
           //println("failure!")
           //println("returned: " + P.string_of_Formula(r))
           Some(Sequent(sig, Nil, List(r)))
         }      
       } catch {
         case e: CHAbort => 
           println("aborted quantifier elimination")
           None
       }
    }

// TODO
    def proceed(sq: Sequent, tm: Long): Option[Sequent] = {
      None
    }


    def abort : Unit = {
      CV.stop()
    }


  }

//------------------------------------------------------------------


  object Mathematica extends Procedure {
    import com.wolfram.jlink._
    import MathematicaUtil._


    var eval = false
    val evalLock = new Lock()

    var aborted = false
    val abortLock = new Lock()




    def applies(sq: Sequent) : Boolean = sq match {
      case Sequent(sig, c,s) =>
        !(c.exists(x => ! canQE(x,sig)) ||  s.exists(x => ! canQE(x,sig)) )
    }


    def proceed(sq: Sequent, tm: Long): Option[Sequent] = sq match {
      case Sequent(sig, c,s) => 
        val fm0 = Binop(Imp, AM.list_conj(c), AM.list_disj(s));
//        val fm = AM.univ_close(fm0);
        val fm = AM.makeQEable(fm0);
        println("about to attempt quantifier elimination on:")
        println(fm.toString)
        println()
        System.out.flush
        val mfm = mathematica_of_formula(fm)
        val mfm_red = 
         new Expr(new Expr(Expr.SYMBOL, "Reduce"),
                  List(mfm, 
                       new Expr(Expr.SYMBOL, "Reals")).toArray)

      
        val mfm_tmt = 
          if(tm > 0){
            new Expr(new Expr(Expr.SYMBOL, "TimeConstrained"),
                     List(mfm_red, 
                          new Expr(Expr.REAL, 
                                   (tm / 1000.0).toString)).toArray)
          }
          else mfm_red

        val check = new Expr(new Expr(Expr.SYMBOL, "Check"), 
                            List(mfm_red, 
                                 new Expr("$Exception"), 
                                 mBlist ).toArray)


       println("\nmathematica version of formula = ")
       println(mfm_tmt)
    
       
       val link = linkLock.synchronized{
                   mbe_link match {
                     case None =>
                       createLink
                     case Some(link1) =>
                       link1
                   }
       }

       try{ 



           link.newPacket()

           println("evaluating expression")

           link.evaluate(mfm_tmt)

         
           var early_abort = false

           println("error code = " + link.error())
           evalLock.synchronized{
             eval = true
             aborted = false
           }
           link.waitForAnswer()
           evalLock.synchronized{
             eval = false
             if(aborted == true ){
               early_abort = true
             }
           }

           println("answer ready")
           println("error code = " + link.error())


         val abortExpr = new Expr(Expr.SYMBOL, "$Aborted")

         val result = link.getExpr();

         evalLock.synchronized{
           if(aborted == true && result != abortExpr ){
             link.newPacket()
             link.evaluate("0")
             link.discardAnswer()
           }
         }


         link.newPacket()

         println("result = " + result)

         if(result == abortExpr) {
           None
         } 
         else if(result == new Expr(Expr.SYMBOL, "True"))
           {
             println("success!")
             
             println("error code = " + link.error())
             Some(Sequent(sig, Nil,List(True)))
           } else {
             // TODO this doesn't actually mean disproved or aborted.
             // should return the actual formula
             println("failure!")
             println("returned: " + result)
             println("error code = " + link.error())
       	     None
           }


       } catch {
         case e:MathLinkException if e.getErrCode() == 11 => 

	     // error code 11 indicates that the mathkernel has died

               println("caught code 11")
               None
       } 
    }  

// TODO
    def proceed(sq: Sequent): Option[Sequent] = {
      proceed(sq, -1)
    }


    def abort : Unit = linkLock.synchronized {
      mbe_link match {
        case Some(lnk) =>
          evalLock.synchronized{
            if (eval == true) {
              println("about to signal an abort. ")
              System.out.flush
              lnk.abortEvaluation()

              aborted = true
            }
          }

        case None =>
      }      
    }



  }

}
