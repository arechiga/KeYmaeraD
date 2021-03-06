dl('load, "examples/creation.dl")
val rl = loopInduction(
  parseFormula(
    "forall i : C. forall j : C. " + 
    "(E(i) = 1 & E(j) = 1 &  ~(i = j) ) ==>" + 
     "    (  (x(i) <  x(j) & v(i) <= v(j) & a(i) <= a(j)) "+
    "  | (x(i) >  x(j) & v(i) >= v(j) & a(i) >= a(j)) ) "

))


val mostthingsT : Tactic = 
  (hpalphaT | alphaT | nonarithcloseT | substT | betaT)*

val everythingT: Tactic = 
  mostthingsT & hidethencloseT


val pred : Formula => Boolean = fm => fm match {
  case Binop(Imp, Not(Atom(R("=", _))), _) => true
  case _ => false
}

val pred2 : Formula => Boolean = fm => fm match {
  case Binop(Imp, Atom(R("=", _)), _) => true
  case _ => false
}

val impleftbranch1 : Tactic = 
  branchT( tryrulepredT(impLeft)(pred),
          List(unitT,
               tryruleT(not) & (substT*) & (nullarizeT*) & hidethencloseT))


val indtct = 
  composelistT(
    hpalpha1T*,
    diffsolveT(RightP(0),Endpoint),
    hpalpha1T*,
    instantiate3T,
    instantiate1T(St("C")),
    impleftbranch1*,
    tryrulepredT(hide)(pred2)*,
    nullarizeT*
  )

val posttct = 
   (hpalpha1T*) & instantiate3T & (nullarizeT*) & (substT*) & everythingT
   

  

dl('gotoroot)
dl('tactic,  branchT(tryruleT(rl),
                     List(tryruleatT(close)(RightP(0)),
                          indtct,
                          posttct
                          )))
