package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestOptimizeSubqueries(t *testing.T) {
	rule := loadRule(t, "OptimizeSubqueries")
	e := newEnv(t)
	left, leftCols := e.scan("u", 1)
	right, rightCols := e.scan("t", 3)
	existsCol := e.f.Metadata().AddColumn("exists_input", types.Int)
	existsInput := e.intValues([]opt.ColumnID{existsCol}, []int{7})
	condition := e.f.ConstructAnd(e.exists(existsInput), e.f.ConstructEq(e.v(leftCols[0]), e.v(rightCols[0])))
	before := e.f.ConstructSemiJoin(left, right, e.filters(condition), &memo.JoinPrivate{})
	projectedCols := opt.ColSet{}
	for _, ordinal := range []int{0, 2} {
		projectedCols.Add(rightCols[ordinal])
	}
	projected := e.f.ConstructProject(right, memo.EmptyProjectionsExpr, projectedCols)
	want := e.f.ConstructSemiJoin(left, projected, e.filters(condition), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	valuesCols := []opt.ColumnID{
		e.f.Metadata().AddColumn("value0", types.Int),
		e.f.Metadata().AddColumn("value1", types.Int),
		e.f.Metadata().AddColumn("value2", types.Int),
	}
	values := e.intValues(valuesCols, []int{1, 2, 3})
	nearMiss := e.f.ConstructSemiJoin(left, values, e.filters(condition), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
