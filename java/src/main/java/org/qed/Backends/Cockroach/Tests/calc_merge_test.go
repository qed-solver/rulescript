package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestCalcMerge(t *testing.T) {
	rule := loadRule(t, "CalcMerge")
	e := newEnv(t)
	s, sc := e.scan("u", 2)
	x, y := e.v(sc[0]), e.v(sc[1])
	b1 := e.f.Metadata().AddColumn("b1", types.Int)
	b2 := e.f.Metadata().AddColumn("b2", types.Int)
	t1 := e.f.Metadata().AddColumn("t1", types.Int)
	t2 := e.f.Metadata().AddColumn("t2", types.Int)
	t3 := e.f.Metadata().AddColumn("t3", types.Int)

	bottom := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(x, y))),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(x, y), b1),
			e.f.ConstructProjectionsItem(e.f.ConstructMinus(x, y), b2),
		},
		opt.ColSet{},
	)
	before := e.f.ConstructProject(
		e.f.ConstructSelect(bottom, e.filters(e.f.ConstructGt(e.v(b1), e.v(b2)))),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(b1), e.v(b2)), t1),
			e.f.ConstructProjectionsItem(e.f.ConstructMinus(e.v(b1), e.v(b2)), t2),
		},
		opt.ColSet{},
	)
	want := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(
			e.f.ConstructLt(x, y),
			e.f.ConstructGt(e.f.ConstructPlus(x, y), e.f.ConstructMinus(x, y)),
		)),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(
				e.f.ConstructPlus(e.f.ConstructPlus(x, y), e.f.ConstructMinus(x, y)), t1),
			e.f.ConstructProjectionsItem(
				e.f.ConstructMinus(e.f.ConstructPlus(x, y), e.f.ConstructMinus(x, y)), t2),
		},
		opt.ColSet{},
	)
	e.expectRewrite(rule, before, want)

	threeOutputs := e.f.ConstructProject(
		e.f.ConstructSelect(bottom, e.filters(e.f.ConstructGt(e.v(b1), e.v(b2)))),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(b1), e.v(b2)), t1),
			e.f.ConstructProjectionsItem(e.f.ConstructMinus(e.v(b1), e.v(b2)), t2),
			e.f.ConstructProjectionsItem(e.v(b1), t3),
		},
		opt.ColSet{},
	)
	e.expectNoMatch(rule, threeOutputs)

	noBottomProject := e.f.ConstructProject(
		e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(x, y))),
		memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(x, y), t1),
			e.f.ConstructProjectionsItem(e.f.ConstructMinus(x, y), t2),
		},
		opt.ColSet{},
	)
	e.expectNoMatch(rule, noBottomProject)
}
