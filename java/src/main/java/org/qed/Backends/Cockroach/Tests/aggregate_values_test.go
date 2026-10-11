package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestAggregateValues(t *testing.T) {
	rule := loadRule(t, "AggregateValues")
	e := newEnv(t)
	s, c := e.scan("u", 1)
	before := e.f.ConstructGroupBy(s, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(c[0]),
	})
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		e.t.Fatalf("%s did not apply to:\n%s", rule.Name, e.format(before))
	}
	out, ok := got.(*memo.ProjectExpr)
	if !ok || len(out.Projections) != 1 || out.Projections[0].Col != c[0] {
		e.t.Fatalf("%s did not produce a projection of the grouping column:\n%s", rule.Name, e.format(got))
	}
	union, ok := out.Input.(*memo.UnionExpr)
	if !ok {
		e.t.Fatalf("%s did not produce a union:\n%s", rule.Name, e.format(got))
	}
	set := union.Private().(*memo.SetPrivate)
	if e.format(union.Left) != e.format(s) || len(set.LeftCols) != 1 || set.LeftCols[0] != c[0] {
		e.t.Fatalf("%s union left branch is not the source scan:\n%s", rule.Name, e.format(got))
	}
	right, ok := union.Right.(*memo.ProjectExpr)
	if !ok || e.format(right.Input) != e.format(s) || len(set.RightCols) != 1 || set.RightCols[0] != right.Projections[0].Col {
		e.t.Fatalf("%s union right branch is not a renamed copy of the source scan:\n%s", rule.Name, e.format(got))
	}
	if out.Projections[0].Element.(*memo.VariableExpr).Col != set.OutCols[0] {
		e.t.Fatalf("%s projection does not read the union output:\n%s", rule.Name, e.format(got))
	}

	ts, tc := e.scan("t", 2)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	aggs := memo.AggregationsExpr{e.f.ConstructAggregationsItem(e.f.ConstructCount(e.v(tc[0])), fcol)}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(ts, aggs, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(tc[0]),
	}))

	e.expectNoMatch(rule, e.f.ConstructGroupBy(ts, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(tc[1]),
	}))

	filtered := e.f.ConstructSelect(ts, e.filters(e.f.ConstructGt(e.v(tc[0]), e.num(1))))
	e.expectNoMatch(rule, e.f.ConstructGroupBy(filtered, nil, &memo.GroupingPrivate{
		GroupingCols: opt.MakeColSet(tc[0]),
	}))

	e.expectNoMatch(rule, s)
}
