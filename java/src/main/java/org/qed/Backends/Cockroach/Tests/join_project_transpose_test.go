package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestJoinProjectTranspose(t *testing.T) {
	rule := loadRule(t, "JoinProjectTranspose")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("t", 2)
	tl := e.f.Metadata().AddColumn("tl", types.Int)
	tr := e.f.Metadata().AddColumn("tr", types.Int)
	leftProject := e.f.ConstructProject(l, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(lc[1])), tl),
	}, opt.ColSet{})
	rightProject := e.f.ConstructProject(r, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(rc[0]), e.v(rc[1])), tr),
	}, opt.ColSet{})
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructLt(e.v(tl), e.v(tr))) }
	before := e.f.ConstructInnerJoin(leftProject, rightProject, on(), &memo.JoinPrivate{})
	inner := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructLt(
		e.f.ConstructPlus(e.v(lc[0]), e.v(lc[1])),
		e.f.ConstructPlus(e.v(rc[0]), e.v(rc[1])),
	)), &memo.JoinPrivate{})
	want := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(lc[1])), tl),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(rc[0]), e.v(rc[1])), tr),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, inner)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(leftProject, rightProject, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		l, rightProject, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(tr))), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		leftProject, r, e.filters(e.f.ConstructLt(e.v(tl), e.v(rc[0]))), &memo.JoinPrivate{}))
}
