package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyExcept(t *testing.T) {
	rule := loadRule(t, "SimplifyExcept")

	t.Run("unique-left-except", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("t", 1)
		right, rightCols := e.scan("u", 1)
		before := e.f.ConstructExcept(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{leftCols[0]}, RightCols: opt.ColList{rightCols[0]},
			OutCols: opt.ColList{leftCols[0]},
		})
		condition := e.filters(e.f.ConstructIs(e.v(leftCols[0]), e.v(rightCols[0])))
		want := e.f.ConstructAntiJoin(left, right, condition, &memo.JoinPrivate{})
		e.expectRewrite(rule, before, want)
	})

	t.Run("except-all-near-miss", func(t *testing.T) {
		e := newEnv(t)
		left, leftCols := e.scan("t", 1)
		right, rightCols := e.scan("u", 1)
		plan := e.f.ConstructExceptAll(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{leftCols[0]}, RightCols: opt.ColList{rightCols[0]},
			OutCols: opt.ColList{leftCols[0]},
		})
		e.expectNoMatch(rule, plan)
	})
}
