package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestMultiJoinProjectTranspose(t *testing.T) {
	rule := loadRule(t, "MultiJoinProjectTranspose")
	e := newEnv(t)
	l, lc := e.scan("u", 2)
	r, rc := e.scan("t", 2)
	tl := e.f.Metadata().AddColumn("tl", types.Int)
	leftItem := func() memo.ProjectionsExpr {
		return memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(lc[1])), tl),
		}
	}
	leftProject := e.f.ConstructProject(l, leftItem(), opt.ColSet{})
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructLt(e.v(tl), e.v(rc[0]))) }
	before := e.f.ConstructInnerJoin(leftProject, r, on(), &memo.JoinPrivate{})
	inner := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructLt(
		e.f.ConstructPlus(e.v(lc[0]), e.v(lc[1])), e.v(rc[0]),
	)), &memo.JoinPrivate{})
	want := e.f.ConstructProject(inner, leftItem(), opt.MakeColSet(rc[0], rc[1]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		l, r, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(leftProject, r, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		e.f.ConstructProject(l, leftItem(), opt.MakeColSet(lc[1])), r, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(
		leftProject, r, e.filters(e.f.ConstructLt(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{}))
}
