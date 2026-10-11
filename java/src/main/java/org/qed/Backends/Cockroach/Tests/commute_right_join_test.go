package rulescript_test

import (
	"strings"
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCommuteRightJoin(t *testing.T) {
	rule := loadRule(t, "CommuteRightJoin")
	e := newEnv(t)
	l, lc := e.scan("t", 1)
	r, rc := e.scan("u", 1)
	on := func() memo.FiltersExpr { return e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))) }
	before := e.f.ConstructRightJoin(l, r, on(), &memo.JoinPrivate{})
	inner := e.f.ConstructLeftJoin(r, l, on(), &memo.JoinPrivate{})
	want := e.f.ConstructProject(inner, nil, opt.MakeColSet(lc[0], rc[0]))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on(), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, on(), &memo.JoinPrivate{}))
	l2, _ := e.scan("t", 2)
	e.expectNoMatch(rule, e.f.ConstructRightJoin(l2, r, on(), &memo.JoinPrivate{}))
}

func TestSQLCommuteRightJoin(t *testing.T) {
	sql := "SELECT t.a, u.x FROM t RIGHT JOIN u ON t.a = u.x"
	prune := func(r opt.RuleName) bool { return strings.HasPrefix(r.String(), "Prune") }
	before, _ := planFor(t, sql, prune)
	if !strings.Contains(before, "right-join") {
		t.Fatalf("expected a right join:\n%s", before)
	}
	got, fired := planFor(t, sql, prune, "CommuteRightJoin")
	if strings.Join(fired, ",") != "CommuteRightJoin" || strings.Contains(got, "right-join") {
		t.Fatalf("expected the right join to be commuted, got %v:\n%s", fired, got)
	}
}
