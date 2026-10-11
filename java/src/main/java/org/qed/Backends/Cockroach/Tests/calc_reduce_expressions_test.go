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
	s, sc := e.scan("u", 2)
	out1 := e.f.Metadata().AddColumn("e1", types.Int)
	out2 := e.f.Metadata().AddColumn("e2", types.Int)
	items := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.v(sc[1])), out1),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), out2),
		}
	}
	falseFilter := e.f.ConstructSelect(s, e.filters(e.f.ConstructFalse()))
	before := e.f.ConstructProject(falseFilter, items(), opt.ColSet{})
	e.expectRewrite(rule, before, e.empty(out1, out2))

	trueFilter := e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(sc[0]), e.num(0))))
	e.expectNoMatch(rule, e.f.ConstructProject(trueFilter, items(), opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, items(), opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(falseFilter, items()[:1], opt.ColSet{}))
	e.expectNoMatch(rule, falseFilter)
}
