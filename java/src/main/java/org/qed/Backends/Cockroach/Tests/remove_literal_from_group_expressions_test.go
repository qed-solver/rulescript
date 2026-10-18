package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRemoveLiteralFromGroupExpressions(t *testing.T) {
	rule := loadRule(t, "RemoveLiteralFromGroupExpressions")
	e := newEnv(t)
	scan, cols := e.scan("t", 2)
	constantCol := e.f.Metadata().AddColumn("constant", types.Int)
	agg1 := e.f.Metadata().AddColumn("sum", types.Int)
	agg2 := e.f.Metadata().AddColumn("max", types.Int)
	keyInput := e.f.ConstructProject(scan,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.num(7), constantCol)},
		opt.MakeColSet(cols...),
	)
	before := e.f.ConstructGroupBy(keyInput,
		memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[1])), agg1),
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(cols[1])), agg2),
		},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0], constantCol)},
	)
	reduced := e.f.ConstructGroupBy(scan,
		memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[1])), agg1),
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(cols[1])), agg2),
		},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])},
	)
	want := e.f.ConstructProject(reduced,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.num(7), constantCol)},
		opt.MakeColSet(cols[0], agg1, agg2),
	)
	e.expectRewrite(rule, before, want)

	nearInput := e.f.ConstructProject(scan,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.num(7), constantCol)},
		opt.MakeColSet(cols...),
	)
	near := e.f.ConstructGroupBy(nearInput,
		memo.AggregationsExpr{
			e.f.ConstructAggregationsItem(e.f.ConstructSumInt(e.v(cols[1])), agg1),
			e.f.ConstructAggregationsItem(e.f.ConstructMax(e.v(cols[1])), agg2),
		},
		&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0], constantCol, cols[1])},
	)
	e.expectNoMatch(rule, near)
}
