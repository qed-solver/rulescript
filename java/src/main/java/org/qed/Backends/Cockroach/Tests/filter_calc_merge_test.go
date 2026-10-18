package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFilterCalcMerge(t *testing.T) {
	rule := loadRule(t, "FilterCalcMerge")
	e := newEnv(t)
	s, sc := e.scan("u", 2)
	bottom := e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.v(sc[0]), e.v(sc[1]))))
	c0 := e.f.Metadata().AddColumn("e0", types.Int)
	c1 := e.f.Metadata().AddColumn("e1", types.Int)
	projections := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.v(sc[1])), c0),
			e.f.ConstructProjectionsItem(e.f.ConstructMinus(e.v(sc[0]), e.v(sc[1])), c1),
		}
	}
	gt := func() opt.ScalarExpr { return e.f.ConstructGt(e.v(c0), e.v(c1)) }
	before := e.f.ConstructSelect(
		e.f.ConstructProject(bottom, projections(), opt.ColSet{}),
		e.filters(gt()),
	)
	after := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(
			e.f.ConstructGt(
				e.f.ConstructPlus(e.v(sc[0]), e.v(sc[1])),
				e.f.ConstructMinus(e.v(sc[0]), e.v(sc[1])),
			),
			e.f.ConstructLt(e.v(sc[0]), e.v(sc[1])),
		)),
		projections(),
		opt.ColSet{},
	)
	e.expectRewrite(rule, before, after)

	e.expectNoMatch(rule, e.f.ConstructSelect(
		e.f.ConstructProject(s, projections(), opt.ColSet{}),
		e.filters(gt()),
	))

	c2 := e.f.Metadata().AddColumn("p", types.Int)
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructProject(bottom, projections(), opt.ColSet{}),
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(c0), c2)},
		opt.ColSet{},
	))
}
