dl('load, "examples/llcsimple.dl")
val rl = loopInduction(
  parseFormula(
    "(b()>0 & B() > 0 & B() > b() & ~(f() = l()) & eps() > 0) &" + 
    "(((b()*B()*x(l()) > b()*B()*x(f()) + " + 
    "(1/2) * (B()*v(f())^2 -  b()*v(l())^2) & " +
    "x(l()) > x(f()) &" +
    "v(f()) >= 0 &" +
    "v(l()) >= 0 )    )   )"))


val cuttct = cutT(
  parseFormula(
    "b()*B()*X1>b()*B()*X2+1/2*(B()*V1^2-b()*V2^2)+" + 
     "B()*(A()+b())*(1/2*A()*eps()^2+eps()*V1)"
  ),
  parseFormula(
    "b()*B()*X1>b()*B()*X2+1/2*(B()*V1^2-b()*V2^2)+" + 
     "B()*(A()+b())*(1/2*A()*s()^2+s()*V1)"
  )
)
  
val everythingT: Tactic = 
  composeT(
    repeatT(
      eitherlistT(List(hpalphaT, 
                       alphaT, 
                       nonarithcloseT,
                       betaT, 
                       substT))),
    eitherT(nonarithcloseT, hidethencloseT))





val ch_brake = 
  composelistT(List(repeatT(hpalpha1T),
                    diffsolveT(RightP(1),Endpoint),
                    repeatT(hpalpha1T),
                    instantiate0T(St("C")),
                    repeatT(substT),
                    hideunivsT(St("C")),
                    repeatT(nullarizeT),
                    repeatT(vacuousT),
                    everythingT
                      ))

val whatev_finish = composelistT(List(
        repeatT(nullarizeT),
        cuttct
    ))


val ch_whatev = 
  composelistT(List(repeatT(hpalpha1T),
                    diffsolveT(RightP(1),Standard),
                    tryruleT(update),
                    repeatT(hpalpha1T),
                    instantiate0T(St("C")),
                    repeatT(substT),
                    hideunivsT(St("C")),
                    repeatT(hpalpha1T),
                    repeatT(vacuousT),
                    branchT(tryruleT(impLeft),
                            List(branchT(tryruleT(impLeft),
                                         List(whatev_finish,
                                              composelistT(
                                                List(tryruleT(not),
                                                     alleasyT)))
                                       ),
                                 composelistT(
                                   List(tryruleT(not),
                                        tryruleT(close)))))
                  ))



val indtct =                           
  composeT(
   repeatT(eitherT(hpalphaT,alphaT)),
   branchT(tryruleT(choose),
           List(ch_brake,ch_whatev)))

    



dl('gotoroot)
dl('tactic,  branchT(tryruleT(rl),
                     List(tryruleatT(close)(RightP(0)),
                          indtct,
                          repeatT(trylistofrulesT(List(close,andLeft)))
                          )))


/*
dl('tactic, trylistofrulesT(List(rl)))
dl('tactic, applyToLeavesT(tryruleatT(close) (RightP(0))))
dl('tactic, applyToLeavesT(repeatT(alphaT)))
dl('tactic, applyToLeavesT(tryruleatT(close) (RightP(0))))
dl('tactic, applyToLeavesT(repeatT(eitherT(hpalphaT,alphaT))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(
  qDiffSolve(Endpoint)(List(
    parseFormula("forall s . x(s, i) = (1/2) *a(i) * s^2 + v(i) * s + x(i)"),
    parseFormula("forall s . v(s, i) = a(i) * s + v(i)"),
    parseFormula("forall s . t(s) = t()  + s")
    ))))))
*/
