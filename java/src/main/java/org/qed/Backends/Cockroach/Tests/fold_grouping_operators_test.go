package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldGroupingOperators(t *testing.T) {
	rule := loadRule(t, "FoldGroupingOperators")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	key := e.f.Metadata().AddColumn("key", types.Int)
	value := e.f.Metadata().AddColumn("value", types.Int)
	project := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), key),
	}, s.Relational().OutputCols)
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[0])), value),
	}
	inner := e.f.ConstructGroupBy(project, aggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(key),
	})
	before := e.f.ConstructGroupBy(inner, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(key),
	})
	want := e.f.ConstructGroupBy(project, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(key),
	})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructGroupBy(inner, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(value),
	}))
	direct := e.f.ConstructGroupBy(s, aggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	e.expectNoMatch(rule, e.f.ConstructGroupBy(direct, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	}))
}

func TestSQLFoldGroupingOperators(t *testing.T) {
	expectSQLFiresOrNot(t, "FoldGroupingOperators", "SELECT k FROM (SELECT k, sum(v) AS total FROM (VALUES (1,2),(1,3)) AS s(k,v) GROUP BY k) AS q GROUP BY k", false)
}
