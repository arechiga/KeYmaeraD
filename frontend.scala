package DLBanyan

import scala.actors.Actor
import scala.actors.Actor._


object FrontEnd {

  var frontactor = new FrontActor;

//  def begin : Unit = {
  println ("Welcome to DLBanyan.")
  frontactor.start()
//    frontactor ! 'hi
    
//  }


  def cmd(cmd: Symbol): Unit = {
    frontactor !? cmd
  }

  def cmd(cmd: Symbol, arg: Any): Unit = {
    frontactor.!?((cmd,arg))
  }




}
