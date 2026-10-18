package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyIntersectLeft(t *testing.T) {
	rule := loadRule(t, "SimplifyIntersectLeft")
	t.Run("single-column unique left", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("t", 1)
		right, rc := e.scan("u", 1)
		out := e.f.Metadata().AddColumn("intersect", e.f.Metadata().ColumnMeta(lc[0]).Type)
		before := e.f.ConstructIntersect(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out},
		})
		cond := e.f.ConstructIs(e.v(lc[0]), e.v(rc[0]))
		semi := e.f.ConstructSemiJoin(left, right, e.filters(cond), &memo.JoinPrivate{})
		want := e.f.ConstructProject(semi, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(lc[0]), out),
		}, opt.ColSet{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("two-column set is outside scope", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("t", 2)
		right, rc := e.scan("nn3", 2)
		out := make(opt.ColList, 2)
		for i := range out {
			out[i] = e.f.Metadata().AddColumn("intersect", e.f.Metadata().ColumnMeta(lc[i]).Type)
		}
		before := e.f.ConstructIntersect(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{lc[0], lc[1]}, RightCols: opt.ColList{rc[0], rc[1]}, OutCols: out,
		})
		e.expectNoMatch(rule, before)
	})
}
