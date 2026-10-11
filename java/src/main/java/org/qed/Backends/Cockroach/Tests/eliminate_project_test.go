package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestEliminateProject(t *testing.T) {
	rule := loadRule(t, "EliminateProject")
	e := newEnv(t)
	s, cols := e.scan("u", 2)
	identity := e.f.ConstructProject(s, nil, opt.MakeColSet(cols...))
	e.expectRewrite(rule, identity, s)

	e.expectNoMatch(rule, e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0])))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[0]), e.num(0)))))
	e.expectNoMatch(rule, s)
}

func TestSQLEliminateProject(t *testing.T) {
	sql := "SELECT x, y FROM u"
	prune := func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") }
	before, _ := planFor(t, sql, prune)
	if !strings.Contains(before, "project") {
		t.Fatalf("expected an identity project before the rule:\n%s", before)
	}
	got, fired := planFor(t, sql, prune, "EliminateProject")
	if strings.Join(fired, ",") != "EliminateProject" || strings.Contains(got, "project") {
		t.Fatalf("expected the identity project to be removed, got %v:\n%s", fired, got)
	}
}
