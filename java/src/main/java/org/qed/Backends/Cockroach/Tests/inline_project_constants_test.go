package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInlineProjectConstants(t *testing.T) {
	rule := loadRule(t, "InlineProjectConstants")
	e := newEnv(t)
	src, sc := e.scan("t", 1)
	cst, cc := e.scan("u", 1)
	on := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(cc[0])))
	join := e.f.ConstructInnerJoin(src, cst, on, &memo.JoinPrivate{})

	out := e.f.Metadata().AddColumn("out", types.Int)
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), out),
	}, opt.ColSet{})

	inlined := e.f.Metadata().AddColumn("inlined", types.Int)
	want := e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cc[0]), e.num(1)), inlined),
	}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	leftJoin := e.f.ConstructLeftJoin(src, cst, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(leftJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), out),
	}, opt.ColSet{}))

	ltOn := e.filters(e.f.ConstructLt(e.v(sc[0]), e.v(cc[0])))
	ltJoin := e.f.ConstructInnerJoin(src, cst, ltOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(ltJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), out),
	}, opt.ColSet{}))

	wide, wc := e.scan("u", 2)
	wideOn := e.filters(e.f.ConstructEq(e.v(sc[0]), e.v(wc[0])))
	wideJoin := e.f.ConstructInnerJoin(src, wide, wideOn, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(wideJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(sc[0]), e.num(1)), out),
	}, opt.ColSet{}))
}
