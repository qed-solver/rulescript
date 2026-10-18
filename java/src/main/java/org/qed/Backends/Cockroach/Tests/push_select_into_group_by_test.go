package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectIntoGroupBy(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoGroupBy")

	t.Run("pushes grouping key filters", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("nn", 2)
		key, value := cols[0], cols[1]
		countCol := e.f.Metadata().AddColumn("count", e.f.Metadata().ColumnMeta(value).Type)
		grouping := e.f.ConstructGroupBy(input,
			memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(value)), countCol)},
			&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)},
		)
		p := e.f.ConstructGt(e.v(key), e.num(10))
		r := e.f.ConstructLe(e.v(key), e.num(20))
		condition := e.f.ConstructAnd(p, r)
		before := e.f.ConstructSelect(grouping, e.filters(condition))
		filtered := e.f.ConstructSelect(input, e.filters(p, r))
		want := e.f.ConstructGroupBy(filtered,
			memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(value)), countCol)},
			&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)},
		)
		e.expectRewrite(rule, before, want)
	})

	t.Run("keeps aggregate output filters above grouping", func(t *testing.T) {
		e := newEnv(t)
		input, cols := e.scan("nn", 2)
		key, value := cols[0], cols[1]
		countCol := e.f.Metadata().AddColumn("count", e.f.Metadata().ColumnMeta(value).Type)
		grouping := e.f.ConstructGroupBy(input,
			memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(value)), countCol)},
			&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)},
		)
		p := e.f.ConstructGt(e.v(key), e.num(10))
		r := e.f.ConstructLe(e.v(countCol), e.num(20))
		e.expectNoMatch(rule, e.f.ConstructSelect(grouping, e.filters(e.f.ConstructAnd(p, r))))
	})
}
