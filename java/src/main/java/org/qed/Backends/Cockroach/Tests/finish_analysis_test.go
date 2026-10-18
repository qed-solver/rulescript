package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFinishAnalysis(t *testing.T) {
	rule := loadRule(t, "FinishAnalysis")
	e := newEnv(t)
	scan, cols := e.scan("t", 2)
	a, b := cols[0], cols[1]

	buildGroupBy := func(input memo.RelExpr, key opt.ColumnID, agg opt.ScalarExpr, aggCol opt.ColumnID) memo.RelExpr {
		return e.f.ConstructGroupBy(input,
			memo.AggregationsExpr{e.f.ConstructAggregationsItem(agg, aggCol)},
			&memo.GroupingPrivate{GroupingCols: opt.MakeColSet(key)})
	}

	gcol := e.f.Metadata().AddColumn("g", types.Int)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	keyProject := e.f.ConstructProject(scan,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(a), e.num(1)), gcol)},
		opt.MakeColSet(a, b))
	before := buildGroupBy(keyProject, gcol, e.f.ConstructSum(e.v(b).(*memo.VariableExpr)), fcol)

	gcol2 := e.f.Metadata().AddColumn("g2", types.Int)
	fcol2 := e.f.Metadata().AddColumn("f2", types.Int)
	wantProject := e.f.ConstructProject(scan,
		memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(a), e.num(1)), gcol2)},
		opt.MakeColSet(b))
	want := buildGroupBy(wantProject, gcol2, e.f.ConstructSum(e.v(b).(*memo.VariableExpr)), fcol2)
	e.expectRewriteUpToFreshColumns(rule, before, want)

	distinctAgg := e.f.ConstructAggDistinct(e.f.ConstructSum(e.v(b).(*memo.VariableExpr)))
	e.expectNoMatch(rule, buildGroupBy(keyProject, gcol, distinctAgg, fcol))

	e.expectNoMatch(rule, buildGroupBy(scan, a, e.f.ConstructSum(e.v(b).(*memo.VariableExpr)), fcol))
}
