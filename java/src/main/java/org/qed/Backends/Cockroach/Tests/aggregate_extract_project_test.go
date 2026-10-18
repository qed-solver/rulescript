package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateExtractProject(t *testing.T) {
	rule := loadRule(t, "AggregateExtractProject")
	e := newEnv(t)
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("nn", 1)
	on := e.filters()
	sumCol := e.f.Metadata().AddColumn("sum", types.Int)
	sumItems := func() memo.AggregationsExpr {
		return memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSum(e.v(bc[0])), sumCol),
		}
	}
	grouping := func(col opt.ColumnID) *memo.GroupingPrivate {
		return &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(col)}
	}

	ab := e.f.ConstructInnerJoin(a, b, on, &memo.JoinPrivate{})
	abc := e.f.ConstructInnerJoin(ab, c, on, &memo.JoinPrivate{})
	before := e.f.ConstructGroupBy(abc, sumItems(), grouping(ac[0]))
	projected := e.f.ConstructProject(abc, nil, opt.MakeColSet(ac[0], bc[0]))
	want := e.f.ConstructGroupBy(projected, sumItems(), grouping(ac[0]))
	e.expectRewrite(rule, before, want)

	leftABC := e.f.ConstructLeftJoin(ab, c, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(leftABC, sumItems(), grouping(ac[0])))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(ab, sumItems(), grouping(ac[0])))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(abc, sumItems(), grouping(cc[0])))
}
