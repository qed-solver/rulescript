package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestCalcReduceExpressions(t *testing.T) {
	rule := loadRule(t, "CalcReduceExpressions")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	e1 := e.f.Metadata().AddColumn("e1", types.Int)
	e2 := e.f.Metadata().AddColumn("e2", types.Int)
	projs := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(lc[1])), e1),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.num(1)), e2),
		}
	}

	falseSel := e.f.ConstructSelect(l, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, e.f.ConstructProject(falseSel, projs(), opt.ColSet{}), e.empty(e1, e2))

	trueSel := e.f.ConstructSelect(l, e.filters(e.f.ConstructTrue()))
	e.expectNoMatch(rule, e.f.ConstructProject(trueSel, projs(), opt.ColSet{}))

	eqSel := e.f.ConstructSelect(l, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(lc[1]))))
	e.expectNoMatch(rule, e.f.ConstructProject(eqSel, projs(), opt.ColSet{}))

	wide, wc := e.scan("t", 3)
	wideSel := e.f.ConstructSelect(wide, e.filters(e.f.ConstructFalse()))
	wideProjs := memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(wc[0]), e.v(wc[1])), e1),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(wc[0]), e.num(1)), e2),
	}
	e.expectNoMatch(rule, e.f.ConstructProject(wideSel, wideProjs, opt.ColSet{}))

	e.expectNoMatch(rule, falseSel)
}
