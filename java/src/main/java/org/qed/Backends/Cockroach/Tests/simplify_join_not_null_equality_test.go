package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestSimplifyJoinNotNullEquality(t *testing.T) {
	rule := loadRule(t, "SimplifyJoinNotNullEquality")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	eq := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	rest := e.f.ConstructGt(e.v(lc[0]), e.num(0))
	isNotFalse := e.f.ConstructIsNot(eq, e.f.ConstructFalse())
	before := e.f.ConstructInnerJoin(l, r, e.filters(isNotFalse, rest), &memo.JoinPrivate{})
	want := e.f.ConstructInnerJoin(l, r, e.filters(eq, rest), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructIsNot(eq, e.f.ConstructTrue()), rest), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(isNotFalse, rest), &memo.JoinPrivate{}))
}

func TestSQLSimplifyJoinNotNullEquality(t *testing.T) {
	expectSQLFires(t, "SimplifyJoinNotNullEquality", "SELECT * FROM (VALUES (1), (2)) AS u(x) JOIN (VALUES (1), (2)) AS t(a) ON (u.x=t.a) IS NOT FALSE AND u.x>0")
}
