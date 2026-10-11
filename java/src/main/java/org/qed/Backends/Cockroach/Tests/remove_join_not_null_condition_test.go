package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestRemoveJoinNotNullCondition(t *testing.T) {
	rule := loadRule(t, "RemoveJoinNotNullCondition")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	rest := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	reject := e.f.ConstructIsNot(e.v(lc[0]), memo.NullSingleton)
	before := e.f.ConstructLeftJoin(l, r, e.filters(reject, rest), &memo.JoinPrivate{})
	want := e.f.ConstructLeftJoin(l, r, e.filters(rest), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(rest, reject), &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, e.filters(e.f.ConstructIsNot(e.v(rc[0]), memo.NullSingleton), rest), &memo.JoinPrivate{}))
}

func TestSQLRemoveJoinNotNullCondition(t *testing.T) {
	expectSQLFires(t, "RemoveJoinNotNullCondition", "SELECT * FROM (VALUES (1), (2)) AS u(x) LEFT JOIN (VALUES (1), (2)) AS t(a) ON u.x IS NOT NULL AND u.x=t.a")
}
