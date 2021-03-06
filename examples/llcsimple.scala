dl('load, "examples/llcsimple.dl")
val rl = loopInduction(
  parseFormula(
    "(A() > 0 & b()>0 & B() > 0 & B() > b() & ~(f() = l()) & eps() > 0) &" + 
    "(((b()*B()*x(l()) > b()*B()*x(f()) + " + 
    "(1/2) * (B()*v(f())^2 -  b()*v(l())^2) & " +
    "x(l()) > x(f()) &" +
    "v(f()) >= 0 &" +
    "v(l()) >= 0 )    )   )"))


val cuttct = cutT(
  DirectedCut,
  parseFormula(
    "b()*B()*X1>b()*B()*X2+1/2*(B()*V1^2-b()*V2^2)+" + 
     "B()*(A()+b())*(1/2*A()*eps()^2+eps()*V1)"
  ),
  parseFormula(
    "b()*B()*X1>b()*B()*X2+1/2*(B()*V1^2-b()*V2^2)+" + 
     "B()*(A()+b())*(1/2*A()*s()^2+s()*V1)"
  )
)

val cuttct2 = cutT(
  StandardCut,
  parseFormula(
    "b()*B()*X1>b()*B()*X2+1/2*(B()*V1^2-b()*V2^2)+" + 
     "B()*(A()+b())*(1/2*A()*eps()^2+eps()*V1)"
  ),
  parseFormula(
     "B()*(A()+b())*(1/2*A()*s()^2+s()*V1) <= " + 
     "B()*(A()+b())*(1/2*A()*eps()^2+eps()*V1) "
  )
)
  
val mostthingsT = 
    repeatT(
      eitherlistT(hpalphaT, 
                  alphaT, 
                  nonarithcloseT,
                  betaT, 
                  substT))


val everythingT: Tactic = 
  composeT(
    repeatT(
      eitherlistT(hpalphaT, 
                  alphaT, 
                  nonarithcloseT,
                  betaT, 
                  substT)),
    eitherT(nonarithcloseT, hidethencloseT))





val ch_brake = 
  composelistT(repeatT(hpalpha1T),
               diffsolveT(RightP(1),Endpoint),
               repeatT(hpalpha1T),
               instantiate0T(St("C")),
               repeatT(substT),
               hideunivsT(St("C")),
               repeatT(nullarizeT),
               repeatT(vacuousT),
               everythingT
                      )

val keepfm1 = parseFormula("B() * (A() + b()) * C <= D")
val keepfm2 = parseFormula("b() * B()  * X > b() * B() * X1 + C + D")
val hidefm3 = parseFormula("b() * B()  * X > b() * B() * X1 + C ")


val whatev_finish = composelistT(
        repeatT(nullarizeT),
        repeatT(substT),
        branchT(cuttct, 
                List(branchT(cuttct2,
                             List(
                               composelistT(
                                   repeatT(hidematchT(List(keepfm2,hidefm3))),
                                   everythingT
                               ),
                               composeT(repeatT(
                                 hidenotmatchT(List(keepfm1,keepfm2))),
                                        arithT))),
                     composelistT(
                       mostthingsT,
                       hidecantqeT,
                       hidematchT(List(hidefm3)),
                       everythingT
                     )))
    )







val ch_whatev = 
  composelistT(repeatT(hpalpha1T),
               diffsolveT(RightP(1),Endpoint),
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
                                           tryruleT(not),
                                           alleasyT))
                                  ),
                            composelistT(
                                   tryruleT(not),
                                        tryruleT(close))))
                  )



val indtct =                           
  composeT(
   repeatT(eitherT(hpalphaT,alphaT)),
   branchT(tryruleT(andRight),
           List(ch_brake,ch_whatev)))

    



dl('gotoroot)
dl('tactic,  branchT(tryruleT(rl),
                     List(tryruleatT(close)(RightP(0)),
                          indtct,
                          repeatT(trylistofrulesT(List(close,andLeft)))
                          )))


