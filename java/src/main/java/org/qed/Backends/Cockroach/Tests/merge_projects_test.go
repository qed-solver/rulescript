package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestMergeProjects(t *testing.T) {
	rule := loadRule(t, "MergeProjects")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	bottom := e.f.Metadata().AddColumn("bottom", types.Int)
	top := e.f.Metadata().AddColumn("top", types.Int)
	inner := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), bottom),
	}, opt.MakeColSet(cols[0]))
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(cols[0]), e.num(2)), top),
	}, opt.ColSet{})
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(cols[0]), e.num(2)), top),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(bottom), e.num(2)), top),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(cols[0]), e.num(2)), top),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, inner)
}

func TestSQLMergeProjects(t *testing.T) {
	sql := "SELECT x * 2 FROM (SELECT x, x + 1 AS b FROM u)"
	before, _ := planFor(t, sql, nil)
	if strings.Count(before, "project\n") < 2 {
		t.Fatalf("expected nested projects before the rule:\n%s", before)
	}
	got, fired := planFor(t, sql, nil, "MergeProjects")
	if strings.Join(fired, ",") != "MergeProjects" || strings.Count(got, "project\n") != 1 {
		t.Fatalf("expected one project, got %v:\n%s", fired, got)
	}
}
