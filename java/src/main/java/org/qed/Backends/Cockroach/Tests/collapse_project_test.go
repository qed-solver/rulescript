package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestCollapseProject(t *testing.T) {
	rule := loadRule(t, "CollapseProject")
	e := newEnv(t)
	s, sc := e.scan("t", 2)
	a, b := sc[0], sc[1]
	e0 := e.f.Metadata().AddColumn("e0", types.Int)
	p0 := e.f.Metadata().AddColumn("p0", types.Int)
	p1 := e.f.Metadata().AddColumn("p1", types.Int)

	lower := e.f.ConstructProject(s,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(a), e.v(b)), e0)},
		opt.MakeColSet(a))
	before := e.f.ConstructProject(lower,
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(e0), e.v(a)), p0),
			e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(e0), e.v(a)), p1),
		},
		opt.ColSet{})
	want := e.f.ConstructProject(s,
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(
				e.f.ConstructPlus(e.f.ConstructPlus(e.v(a), e.v(b)), e.v(a)), p0),
			e.f.ConstructProjectionsItem(
				e.f.ConstructMult(e.f.ConstructPlus(e.v(a), e.v(b)), e.v(a)), p1),
		},
		opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e1 := e.f.Metadata().AddColumn("e1", types.Int)
	computedLower := e.f.ConstructProject(s,
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(a), e.v(b)), e0),
			e.f.ConstructProjectionsItem(e.f.ConstructMinus(e.v(a), e.v(b)), e1),
		},
		opt.ColSet{})
	computedBefore := e.f.ConstructProject(computedLower,
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(e0), e.v(e1)), p0),
			e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(e0), e.v(e1)), p1),
		},
		opt.ColSet{})
	e.expectNoMatch(rule, computedBefore)

	filtered := e.f.ConstructSelect(lower,
		e.filters(e.f.ConstructGt(e.v(e0), e.num(0))))
	e.expectNoMatch(rule, e.f.ConstructProject(filtered,
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(e0), e.v(a)), p0),
			e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(e0), e.v(a)), p1),
		},
		opt.ColSet{}))
}
