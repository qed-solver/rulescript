package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneJoinRightCols(t *testing.T) {
	rule := loadRule(t, "PruneJoinRightCols")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 2)
	out := e.f.Metadata().AddColumn("sum", types.Int)
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))) }
	join := e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{})
	before := e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), out),
	}, opt.ColSet{})
	rpruned := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0]))
	wantJoin := e.f.ConstructInnerJoin(l, rpruned, on(), &memo.JoinPrivate{})
	want := e.f.ConstructProject(wantJoin, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	wrongOn := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[1]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructProject(wrongOn, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(join, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[1])), out),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(l, r, on(), &memo.JoinPrivate{}), memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(lc[0]), e.v(rc[0])), out),
	}, opt.ColSet{}))
}

func TestSQLPruneJoinRightCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneJoinRightCols", "SELECT l.a+r.b FROM (VALUES (1),(2)) AS l(a) JOIN (VALUES (1,2),(3,4)) AS r(b,z) ON l.a=r.b", true)
}
