package rulescript_test

import (
	"fmt"
	"regexp"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/rulescript"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

var freshColumnRef = regexp.MustCompile(`\((item|var) ([0-9]+)`)

func normalizeFreshColumns(plan string, keep opt.ColumnID) string {
	labels := make(map[string]string)
	return freshColumnRef.ReplaceAllStringFunc(plan, func(m string) string {
		sub := freshColumnRef.FindStringSubmatch(m)
		if sub[2] == fmt.Sprint(keep) {
			return m
		}
		label, ok := labels[sub[2]]
		if !ok {
			label = fmt.Sprintf("fresh%d", len(labels)+1)
			labels[sub[2]] = label
		}
		return fmt.Sprintf("(%s %s", sub[1], label)
	})
}

func (e *env) expectRewriteFreshColumns(rule *rulescript.Rule, before, want memo.RelExpr, keep opt.ColumnID) {
	e.t.Helper()
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		e.t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	g := normalizeFreshColumns(e.format(got), keep)
	w := normalizeFreshColumns(e.format(want), keep)
	if g != w {
		e.t.Fatalf("%s produced an unexpected plan\ninput:\n%s\ngot:\n%s\nwant:\n%s",
			rule.Name, e.format(before), e.format(got), e.format(want))
	}
}

func TestAggregateValues(t *testing.T) {
	rule := loadRule(t, "AggregateValues")
	e := newEnv(t)
	s, sc := e.scan("u", 1)
	before := e.f.ConstructGroupBy(s, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])})
	dup := e.f.Metadata().AddColumn("dup", types.Int)
	out := e.f.Metadata().AddColumn("union", types.Int)
	right := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(sc[0]), dup),
	}, opt.ColSet{})
	union := e.f.ConstructUnion(s, right, &memo.SetPrivate{
		LeftCols: opt.ColList{sc[0]}, RightCols: opt.ColList{dup}, OutCols: opt.ColList{out},
	})
	want := e.f.ConstructProject(union, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.v(out), sc[0]),
	}, opt.ColSet{})
	e.expectRewriteFreshColumns(rule, before, want, sc[0])
	e.expectNoMatch(rule, e.f.ConstructScalarGroupBy(s, memo.AggregationsExpr{}, &memo.GroupingPrivate{}))
}

func TestAggregateValuesGroupSubset(t *testing.T) {
	rule := loadRule(t, "AggregateValues")
	e := newEnv(t)
	s, sc := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, memo.AggregationsExpr{}, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(sc[0])}))
}
