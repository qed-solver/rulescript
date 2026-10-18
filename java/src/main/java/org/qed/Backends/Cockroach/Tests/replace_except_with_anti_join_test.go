package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestReplaceExceptWithAntiJoin(t *testing.T) {
	rule := loadRule(t, "ReplaceExceptWithAntiJoin")
	t.Run("two columns", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("t", 2)
		right, rc := e.scan("t", 2)
		before := e.f.ConstructExcept(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{lc[0], lc[1]}, RightCols: opt.ColList{rc[0], rc[1]}, OutCols: opt.ColList{lc[0], lc[1]},
		})
		cond := e.filters(
			e.f.DynamicConstruct(opt.IsOp, e.v(lc[0]), e.v(rc[0])).(opt.ScalarExpr),
			e.f.DynamicConstruct(opt.IsOp, e.v(lc[1]), e.v(rc[1])).(opt.ScalarExpr),
		)
		anti := e.f.ConstructAntiJoin(left, right, cond, &memo.JoinPrivate{})
		want := e.f.ConstructGroupBy(anti, memo.EmptyAggregationsExpr, &memo.GroupingPrivate{
			GroupingCols: opt.MakeColSet(lc[0], lc[1]),
		})
		e.expectRewrite(rule, before, want)
	})
	t.Run("three columns", func(t *testing.T) {
		e := newEnv(t)
		left, lc := e.scan("t", 3)
		right, rc := e.scan("t", 3)
		plan := e.f.ConstructExcept(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{lc[0], lc[1], lc[2]}, RightCols: opt.ColList{rc[0], rc[1], rc[2]}, OutCols: opt.ColList{lc[0], lc[1], lc[2]},
		})
		e.expectNoMatch(rule, plan)
	})
}
