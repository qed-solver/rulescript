package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateValues(t *testing.T) {
	rule := loadRule(t, "AggregateValues")
	e := newEnv(t)
	s, c := e.scan("u", 1)
	before := e.f.ConstructGroupBy(s, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(c[0]),
	})
	outCols := opt.ColList{e.f.Metadata().AddColumn("o", types.Int)}
	want := e.f.ConstructUnion(s, s, &memo.SetPrivate{
		LeftCols:  opt.ColList(c),
		RightCols: opt.ColList(c),
		OutCols:   outCols,
	})
	e.expectRewrite(rule, before, want)

	ts, tc := e.scan("t", 2)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(tc[0])), fcol)}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(ts, aggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(tc[0]),
	}))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(ts, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(tc[1]),
	}))

	filtered := e.f.ConstructSelect(ts, e.filters(e.f.ConstructGt(e.v(tc[0]), e.num(1))))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(filtered, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(tc[0]),
	}))

	e.expectNoMatch(rule, s)
}
