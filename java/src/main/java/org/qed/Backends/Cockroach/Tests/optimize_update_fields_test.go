package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestOptimizeUpdateFields(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "OptimizeUpdateFields")
	source, cols := e.scan("t", 2)
	a, b := e.v(cols[0]), e.v(cols[1])
	e1 := e.f.ConstructPlus(a, e.num(10))
	e2 := e.f.ConstructPlus(b, e.num(20))
	e3 := e.f.ConstructMinus(a, e.num(30))
	a1 := e.f.Metadata().AddColumn("a1", types.Int)
	b1 := e.f.Metadata().AddColumn("b1", types.Int)
	e1Col := e.f.Metadata().AddColumn("e1", types.Int)
	bAlias := e.f.Metadata().AddColumn("b_alias", types.Int)
	p1 := e.f.ConstructProject(source, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(a, a1),
		e.f.ConstructProjectionsItem(b, b1),
		e.f.ConstructProjectionsItem(e1, e1Col),
		e.f.ConstructProjectionsItem(b, bAlias),
	}, opt.ColSet{})
	a2 := e.f.Metadata().AddColumn("a2", types.Int)
	b2 := e.f.Metadata().AddColumn("b2", types.Int)
	e2Col := e.f.Metadata().AddColumn("e2", types.Int)
	p2 := e.f.ConstructProject(p1, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(a1), a2),
		e.f.ConstructProjectionsItem(e.v(b1), b2),
		e.f.ConstructProjectionsItem(e.v(e1Col), e.f.Metadata().AddColumn("e1_carried", types.Int)),
		e.f.ConstructProjectionsItem(e2, e2Col),
	}, opt.ColSet{})
	e3Col := e.f.Metadata().AddColumn("e3", types.Int)
	e2Carry := e.f.Metadata().AddColumn("e2_carried", types.Int)
	p3 := e.f.ConstructProject(p2, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(a2), e.f.Metadata().AddColumn("a3", types.Int)),
		e.f.ConstructProjectionsItem(e.v(b2), e.f.Metadata().AddColumn("b3", types.Int)),
		e.f.ConstructProjectionsItem(e3, e3Col),
		e.f.ConstructProjectionsItem(e.v(e2Col), e2Carry),
	}, opt.ColSet{})
	outA := e.f.Metadata().AddColumn("out_a", types.Int)
	outB := e.f.Metadata().AddColumn("out_b", types.Int)
	before := e.f.ConstructProject(p3, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(e3Col), outA),
		e.f.ConstructProjectionsItem(e.v(e2Carry), outB),
	}, opt.ColSet{})
	t1 := e.f.ConstructProject(source, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e3, e3Col),
		e.f.ConstructProjectionsItem(b, e.f.Metadata().AddColumn("b_alias_after", types.Int)),
	}, opt.MakeColSet(cols[0], cols[1]))
	t2 := e.f.ConstructProject(t1, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e2, e2Col),
	}, opt.MakeColSet(cols[0], cols[1], e3Col))
	t3 := e.f.ConstructProject(t2, nil, opt.MakeColSet(e2Col, e3Col))
	want := e.f.ConstructProject(t3, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(e3Col), outA),
		e.f.ConstructProjectionsItem(e.v(e2Col), outB),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	nearMissCol := e.f.Metadata().AddColumn("near_miss", types.Int)
	nearMiss := e.f.ConstructProject(p3, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(e3Col), outA),
		e.f.ConstructProjectionsItem(e.v(e2Carry), outB),
		e.f.ConstructProjectionsItem(a, nearMissCol),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
