package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneWindowOutputCols(t *testing.T) {
	rule := loadRule(t, "PruneWindowOutputCols")
	e := newEnv(t)
	l, lc := e.scan("nn", 2)
	r, rc := e.scan("nn", 2)
	w1 := e.f.Metadata().AddColumn("w1", types.Int)
	w2 := e.f.Metadata().AddColumn("w2", types.Int)
	aggs := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[1])), w1),
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[1])), w2),
	}
	window := e.f.ConstructGroupBy(r, aggs, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoin(l, window, on, &memo.JoinPrivate{})
	before := e.f.ConstructProject(join, nil, opt.MakeColSet(lc[0], lc[1], w1))
	pruned := e.f.ConstructGroupBy(r, aggs[:1], &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	want := e.f.ConstructProject(e.f.ConstructInnerJoin(l, pruned, on, &memo.JoinPrivate{}), nil,
		opt.MakeColSet(lc[0], lc[1], w1))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(join, nil, opt.MakeColSet(lc[0], lc[1], w2)))
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(l, window, on, &memo.JoinPrivate{}), nil,
		opt.MakeColSet(lc[0], lc[1], w1)))
}

func TestSQLPruneWindowOutputCols(t *testing.T) {
	expectSQLFiresWith(t, "PruneWindowOutputCols",
		"SELECT n.a, n.b, g.w1 FROM nn n JOIN (SELECT a, max(b) AS w1, min(b) AS w2 FROM nn GROUP BY a) g ON n.a = g.a",
		sqlHelpers{names: []string{"PruneJoinLeftCols", "PruneScanCols", "EliminateProject"}})
}
