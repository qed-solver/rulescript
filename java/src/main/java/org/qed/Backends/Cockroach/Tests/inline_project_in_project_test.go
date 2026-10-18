package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInlineProjectInProject(t *testing.T) {
	rule := loadRule(t, "InlineProjectInProject")
	e := newEnv(t)
	src, sc := e.scan("t", 1)
	x2 := e.f.Metadata().AddColumn("x2", types.Int)
	t1 := e.f.Metadata().AddColumn("t1", types.Int)
	f1 := e.f.Metadata().AddColumn("f1", types.Int)

	fOf := func() opt.ScalarExpr { return e.f.ConstructPlus(e.v(sc[0]), e.num(1)) }
	inner := e.f.ConstructProject(src, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(fOf(), x2)}, opt.ColSet{})
	before := e.f.ConstructProject(
		inner,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(x2), e.num(2)), t1)},
		opt.MakeColSet(x2),
	)
	want := e.f.ConstructProject(src, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(fOf(), e.num(2)), t1),
		e.f.ConstructProjectionsItem(fOf(), f1),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	innerWithPassthrough := e.f.ConstructProject(src, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(fOf(), x2)}, opt.MakeColSet(sc[0]))
	outerOfSourceColumn := e.f.ConstructProject(
		innerWithPassthrough,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(x2), e.num(2)), t1)},
		opt.MakeColSet(sc[0]),
	)
	e.expectNoMatch(rule, outerOfSourceColumn)

	sel := e.f.ConstructSelect(src, e.filters(e.f.ConstructEq(e.v(sc[0]), e.num(1))))
	outerOverSelect := e.f.ConstructProject(
		sel,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(sc[0]), e.num(2)), t1)},
		opt.MakeColSet(sc[0]),
	)
	e.expectNoMatch(rule, outerOverSelect)
}
