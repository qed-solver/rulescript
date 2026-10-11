package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestConvertGroupByToDistinct(t *testing.T) {
	rule := loadRule(t, "ConvertGroupByToDistinct")
	e := newEnv(t)
	s, cols := e.scan("nn", 2)
	before := e.f.ConstructGroupBy(s, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols...)})
	got, ok := rule.Apply(&e.f, before)
	if !ok {
		t.Fatal("ConvertGroupByToDistinct did not apply")
	}
	expected := newEnv(t)
	es, ec := expected.scan("nn", 2)
	rightCols := []opt.ColumnID{
		expected.f.Metadata().AddColumn("right_a", expected.f.Metadata().ColumnMeta(ec[0]).Type),
		expected.f.Metadata().AddColumn("right_b", expected.f.Metadata().ColumnMeta(ec[1]).Type),
	}
	right := expected.f.ConstructProject(es, memo.ProjectionsExpr{
		expected.f.ConstructProjectionsItem(expected.v(ec[0]), rightCols[0]),
		expected.f.ConstructProjectionsItem(expected.v(ec[1]), rightCols[1]),
	}, opt.ColSet{})
	want := expected.f.ConstructIntersect(es, right, &memo.SetPrivate{
		LeftCols: append(opt.ColList{}, ec...), RightCols: append(opt.ColList{}, rightCols...),
		OutCols: append(opt.ColList{}, ec...),
	})
	if e.format(got) != expected.format(want) {
		t.Fatalf("unexpected plan\ngot: %s\nwant: %s", e.format(got), expected.format(want))
	}
	e.expectNoMatch(rule, e.f.ConstructGroupBy(s, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(cols[0])}))
	short, _ := e.scan("u", 1)
	e.expectNoMatch(rule, e.f.ConstructGroupBy(short, nil, &memo.GroupingPrivate{GroupingCols: short.Relational().OutputCols}))
}

func TestSQLConvertGroupByToDistinct(t *testing.T) {
	sql := "SELECT a,b FROM (VALUES (1,2),(3,4)) AS v(a,b) GROUP BY a,b"
	plan, fired := planFor(t, sql, nil, "ConvertGroupByToDistinct")
	if len(fired) == 0 {
		t.Fatalf("ConvertGroupByToDistinct did not fire on %s\n%s", sql, plan)
	}
}
