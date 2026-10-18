package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyLeftJoin(t *testing.T) {
	rule := loadRule(t, "SimplifyLeftJoin")
	e := newEnv(t)

	left, lc := e.scan("nn", 1)
	right, rc := e.scan("nn", 1)
	cond := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructLeftJoin(left, right, cond, &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(left, right, cond, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	other, oc := e.scan("u", 1)
	nearMiss := e.f.ConstructLeftJoin(left, other, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(oc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
