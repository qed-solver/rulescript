package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestEliminateGroupByProject(t *testing.T) {
	rule := loadRule(t, "EliminateGroupByProject")
	e := newEnv(t)
	s, cs := e.scan("t", 2)
	p := e.f.ConstructProject(s, memo.ProjectionsExpr{}, opt.MakeColSet(cs[0]))
	before := e.f.ConstructGroupBy(p, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cs[0])})
	want := e.f.ConstructGroupBy(s, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cs[0])})
	e.expectRewrite(rule, before, want)

	keepSecond := e.f.ConstructProject(s, memo.ProjectionsExpr{}, opt.MakeColSet(cs[1]))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(keepSecond, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cs[1])}))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cs[0])}))

	wide, wc := e.scan("t", 3)
	wideProject := e.f.ConstructProject(wide, memo.ProjectionsExpr{}, opt.MakeColSet(wc[0]))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(wideProject, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(wc[0])}))

	computed := e.f.Metadata().AddColumn("computed", types.Int)
	extra := memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.num(1), computed)}
	withExpr := e.f.ConstructProject(s, extra, opt.MakeColSet(cs[0]))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(withExpr, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cs[0])}))
}
