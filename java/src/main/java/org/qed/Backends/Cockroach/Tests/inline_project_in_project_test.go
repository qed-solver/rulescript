package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestInlineProjectInProject(t *testing.T) {
	rule := loadRule(t, "InlineProjectInProject")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	fcol := e.f.Metadata().AddColumn("f", types.Int)
	tcol := e.f.Metadata().AddColumn("top", types.Int)
	inner := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), fcol),
	}, opt.ColSet{})
	before := e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(fcol), e.num(2)), tcol),
	}, opt.MakeColSet(fcol))
	want := e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), e.num(2)), tcol),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(e.v(cols[0]), e.num(1)), fcol),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructProject(inner, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructMult(e.v(fcol), e.num(2)), tcol),
	}, opt.ColSet{}))
	e.expectNoMatch(rule, inner)
	e.expectNoMatch(rule, e.f.ConstructSelect(inner, e.filters(e.f.ConstructGt(e.v(fcol), e.num(0)))))
}

func TestSQLInlineProjectInProject(t *testing.T) {
	sql := "SELECT x2 * 2 AS z, x2 FROM (SELECT x + 1 AS x2 FROM u)"
	prune := func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") }
	before, _ := planFor(t, sql, prune)
	if strings.Count(before, "project\n") < 2 {
		t.Fatalf("expected nested projects before the rule:\n%s", before)
	}
	got, fired := planFor(t, sql, prune, "InlineProjectInProject")
	if strings.Join(fired, ",") != "InlineProjectInProject" || strings.Count(got, "project\n") != 1 {
		t.Fatalf("expected inlined project, got %v:\n%s", fired, got)
	}
}
