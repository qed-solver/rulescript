package rulescript_test

import (
	"regexp"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestRewriteDistinctAggregates(t *testing.T) {
	rule := loadRule(t, "RewriteDistinctAggregates")
	e := newEnv(t)
	s, _ := e.scan("t", 1)
	c := e.f.Metadata().AddColumn("c", types.Int)
	cnt := e.f.Metadata().AddColumn("cnt", types.Int)
	keyed := e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.num(0), c)}, opt.ColSet{})
	distinct := memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructAggDistinct(e.f.ConstructCount(e.v(c))), cnt),
	}
	before := e.f.ConstructScalarGroupBy(keyed, distinct, &memo.GroupingPrivate{})

	c1 := e.f.Metadata().AddColumn("c1", types.Int)
	g1 := e.f.Metadata().AddColumn("g1", types.Int)
	wide := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(0), c1),
		e.f.ConstructProjectionsItem(e.num(0), g1),
	}, opt.ColSet{})
	c2 := e.f.Metadata().AddColumn("c2", types.Int)
	g2 := e.f.Metadata().AddColumn("g2", types.Int)
	keys := e.f.ConstructProject(wide, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.num(0), c2),
		e.f.ConstructProjectionsItem(e.num(0), g2),
	}, wide.Relational().OutputCols)
	dedup := e.f.ConstructGroupBy(keys, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(c2, g2)})
	want := e.f.ConstructScalarGroupBy(dedup, memo.AggregationsExpr{
		e.f.ConstructAggregationsItem(e.f.ConstructCount(e.num(0)), cnt),
	}, &memo.GroupingPrivate{})
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatalf("RewriteDistinctAggregates did not apply to:\n%s", e.format(before))
	}
	passes := regexp.MustCompile(`pass=\([0-9,]*\)`)
	g, _ := alphaComputedColumns(passes.ReplaceAllString(e.format(got), "pass=()"))
	w, _ := alphaComputedColumns(passes.ReplaceAllString(e.format(want), "pass=()"))
	if g != w {
		t.Fatalf("unexpected plan\ngot:\n%s\nwant:\n%s", g, w)
	}

	other := e.f.ConstructProject(s, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.num(1), c)}, opt.ColSet{})
	e.expectNoMatch(rule, e.f.ConstructScalarGroupBy(other, distinct, &memo.GroupingPrivate{}))
	e.expectNoMatch(rule, want)
}
