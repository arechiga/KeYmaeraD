dl('load, "examples/QdlSimple.dl")
val rl1 = allLeft(Fn("j", Nil));
val rl2 = allLeft(Fn("k", Nil));
dl('gotoroot)
dl('tactic, alleasyT)
dl('tactic, trylistofrulesT(List(rl1)))
dl('tactic, applyToLeavesT(trylistofrulesT(List(rl2))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(substitute))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(substitute))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(impLeft))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(close))))
dl('tactic, applyToLeavesT(tryruleatT(hide)(LeftP(1) )))
dl('tactic, applyToLeavesT(tryruleatT(hide)(LeftP(3) )))
dl('tactic, applyToLeavesT(trylistofrulesT(List(rl1))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(rl2))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(substitute))))
dl('tactic, applyToLeavesT(trylistofrulesT(List(substitute))))
dl('tactic, applyToLeavesT(tryruleatT(hide)(LeftP(5) )))