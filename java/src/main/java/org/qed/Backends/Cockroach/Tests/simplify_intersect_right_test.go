package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestSimplifyIntersectRight(t *testing.T) {
	rule := loadRule(t, "SimplifyIntersectRight")
	t.Run("single-column unique right", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 1)
		right, rc := e.scan("t", 1)
		out := e.f.Metadata().AddColumn("intersect", types.Int)
		before := e.f.ConstructIntersect(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{lc[0]}, RightCols: opt.ColList{rc[0]}, OutCols: opt.ColList{out},
		})
		cond := e.f.ConstructIs(e.v(rc[0]), e.v(lc[0]))
		semi := e.f.ConstructSemiJoin(right, left, e.filters(cond), &memo.JoinPrivate{})
		proj := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.v(rc[0]), out)}
		want := e.f.ConstructProject(semi, proj, opt.ColSet{})
		e.expectRewrite(rule, before, want)
	})
	t.Run("two-column set is outside scope", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("u", 2)
		right, rc := e.scan("t", 2)
		out := make(opt.ColList, 2)
		for i := range out {
			out[i] = e.f.Metadata().AddColumn("intersect", types.Int)
		}
		before := e.f.ConstructIntersect(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{lc[0], lc[1]}, RightCols: opt.ColList{rc[0], rc[1]}, OutCols: out,
		})
		e.expectNoMatch(rule, before)
	})
}
