package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneGroupByCols(t *testing.T) {
	rule := loadRule(t, "PruneGroupByCols")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	extra := e.f.Metadata().AddColumn("extra", types.Int)
	wide := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), extra),
	}, opt.MakeColSet(cols...))
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	gcol := e.f.Metadata().AddColumn("g", types.Int)
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[1])), fcol),
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(cols[2])), gcol),
	}
	grouped := e.f.ConstructGroupBy(wide, aggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	before := e.f.ConstructProject(grouped, nil, opt.MakeColSet(cols[0], fcol, gcol))
	narrow := e.f.ConstructProject(wide, nil, opt.MakeColSet(cols[0], cols[1], cols[2]))
	pruned := e.f.ConstructGroupBy(narrow, aggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(cols[0]),
	})
	want := e.f.ConstructProject(pruned, nil, opt.MakeColSet(cols[0], fcol, gcol))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(grouped, nil, opt.MakeColSet(cols[0], fcol)))
	e.expectNoMatch(rule, e.f.ConstructProject(
		e.f.ConstructGroupBy(s, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])}),
		nil, opt.MakeColSet(cols[0], fcol, gcol)))
}

func TestSQLPruneGroupByCols(t *testing.T) {
	expectSQLFiresOrNot(t, "PruneGroupByCols", "SELECT k, sum(a), max(b) FROM (VALUES (1,2,3,4),(1,3,4,5)) AS v(k,a,b,c) GROUP BY k", false)
}
