package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateExpandDistinctAggregates(t *testing.T) {
	rule := loadRule(t, "AggregateExpandDistinctAggregates")
	e := newEnv(t)
	scan, cols := e.scan("t", 1)
	kcol := e.f.Metadata().AddColumn("k", types.Int)
	xcol := e.f.Metadata().AddColumn("x", types.Int)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	gcol := e.f.Metadata().AddColumn("g", types.Int)
	keyed := func(passthrough opt.ColSet) memo.RelExpr {
		return e.f.ConstructProject(scan, memo.ProjectionsExpr{
			e.f.ConstructProjectionsItem(e.v(cols[0]), kcol),
			e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), xcol),
		}, passthrough)
	}

	before := e.f.ConstructGroupBy(keyed(opt.ColSet{}), memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(xcol))), fcol),
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructMin(e.v(xcol))), gcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(kcol)})
	inner := e.f.ConstructGroupBy(keyed(scan.Relational().OutputCols), memo.AggregationsExpr{}, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(kcol, xcol),
	})
	want := e.f.ConstructGroupBy(inner, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(xcol)), fcol),
		e.f.ConstructAggregationsItem(e.f.ConstructMin(e.v(xcol)), gcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(kcol)})
	e.expectRewrite(rule, before, want)

	mixed := e.f.ConstructGroupBy(keyed(opt.ColSet{}), memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(xcol)), fcol),
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructMin(e.v(xcol))), gcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(kcol)})
	e.expectNoMatch(rule, mixed)

	otherArg := e.f.ConstructGroupBy(keyed(opt.ColSet{}), memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(xcol))), fcol),
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructMin(e.v(kcol))), gcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(kcol)})
	e.expectNoMatch(rule, otherArg)
}
