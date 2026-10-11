package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneWindowInputCols(t *testing.T) {
	rule := loadRule(t, "PruneWindowInputCols")
	e := newEnv(t)
	l, lc := e.scan("nn3", 3)
	r, rc := e.scan("nn3", 3)
	rightProject := e.f.ConstructProject(r, nil, opt.MakeColSet(rc[0], rc[1]))
	wcol := e.f.Metadata().AddColumn("w", types.Int)
	window := e.f.ConstructGroupBy(rightProject, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(rc[1])), wcol),
	}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(rc[0])})
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoin(l, window, on, &memo.JoinPrivate{})
	top := e.f.Metadata().AddColumn("top", types.Int)
	items := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(
		e.f.ConstructPlus(e.f.ConstructPlus(e.v(lc[0]), e.v(lc[1])), e.v(wcol)), top)}
	before := e.f.ConstructProject(join, items, opt.ColSet{})
	leftProject := e.f.ConstructProject(l, nil, opt.MakeColSet(lc[0], lc[1]))
	want := e.f.ConstructProject(e.f.ConstructInnerJoin(leftProject, window, on, &memo.JoinPrivate{}), items, opt.ColSet{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructProject(e.f.ConstructLeftJoin(l, window, on, &memo.JoinPrivate{}), items, opt.ColSet{}))
	badItems := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(
		e.f.ConstructPlus(e.f.ConstructPlus(e.v(lc[0]), e.v(lc[2])), e.v(wcol)), top)}
	e.expectNoMatch(rule, e.f.ConstructProject(join, badItems, opt.ColSet{}))
}

func TestSQLPruneWindowInputCols(t *testing.T) {
	expectSQLFiresWith(t, "PruneWindowInputCols",
		"SELECT n.a + n.b + g.w FROM (SELECT a, b, c FROM nn3) n JOIN (SELECT a, max(b) AS w FROM (SELECT a, b, c FROM nn3) GROUP BY a) g ON n.a = g.a",
		sqlHelpers{limits: map[string]int{"PruneScanCols": 2}, names: []string{"EliminateProject"}})
}
