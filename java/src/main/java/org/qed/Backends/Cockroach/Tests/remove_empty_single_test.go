package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveEmptySingle(t *testing.T) {
	rule := loadRule(t, "RemoveEmptySingle")
	e := newEnv(t)
	c0 := e.f.Metadata().AddColumn("c0", types.Int)
	c1 := e.f.Metadata().AddColumn("c1", types.Int)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	private := &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(c0)}

	input := e.empty(c0)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c0)), fcol)}
	before := e.f.ConstructGroupBy(input, aggs, private)
	want := e.empty(c0, fcol)
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(input, nil, private))

	wide := e.empty(c0, c1)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(wide, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(c1)), fcol),
	}, private))

	s, sc := e.scan("u", 2)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(sc[0])), fcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}))

	e.expectNoMatch(rule, input)
}
