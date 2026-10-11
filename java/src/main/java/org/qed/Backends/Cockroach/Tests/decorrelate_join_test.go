package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestDecorrelateJoin(t *testing.T) {
	rule := loadRule(t, "DecorrelateJoin")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoinApply(l, r, on, &memo.JoinPrivate{})
	e.expectRewrite(rule, before, e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoinApply(l, r, on, &memo.JoinPrivate{}))
}

func TestSQLDecorrelateJoin(t *testing.T) {
	expectSQLFires(t, "DecorrelateJoin", "SELECT * FROM (VALUES (1)) AS l(a) JOIN LATERAL (SELECT b FROM (VALUES (1)) AS r(b)) AS q ON l.a=q.b")
}
