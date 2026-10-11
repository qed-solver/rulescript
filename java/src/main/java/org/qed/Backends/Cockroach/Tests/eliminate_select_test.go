package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
)

func TestEliminateSelect(t *testing.T) {
	rule := loadRule(t, "EliminateSelect")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	e.expectRewrite(rule, e.f.ConstructSelect(s, nil), s)

	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[0]), e.num(1)))))
	e.expectNoMatch(rule, e.f.ConstructProject(s, nil, opt.MakeColSet(cols...)))
	e.expectNoMatch(rule, s)
}

func TestSQLEliminateSelect(t *testing.T) {
	sql := "SELECT a FROM t WHERE TRUE"
	prepare := func(r opt.RuleName) bool { return r.String() == "SimplifySelectFilters" }
	before, _ := planFor(t, sql, prepare)
	if !strings.Contains(before, "select") {
		t.Fatalf("expected a select before the rule, got:\n%s", before)
	}
	got, fired := planFor(t, sql, prepare, "EliminateSelect")
	if strings.Join(fired, ",") != "EliminateSelect" || strings.Contains(got, "select") {
		t.Fatalf("expected the empty select to be removed, got %v:\n%s", fired, got)
	}
}
