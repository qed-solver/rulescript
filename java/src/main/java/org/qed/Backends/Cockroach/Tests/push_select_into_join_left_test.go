package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectIntoJoinLeft(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoJoinLeft")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	p := e.f.ConstructGt(e.v(lc[0]), e.num(0))
	q := e.f.ConstructNe(e.v(lc[0]), e.v(rc[0]))
	before := e.f.ConstructSelect(join, e.filters(p, q))
	filtered := e.f.ConstructSelect(l, e.filters(p))
	want := e.f.ConstructSelect(e.f.ConstructLeftJoin(filtered, r, on, &memo.JoinPrivate{}), e.filters(q))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(q, p)))
	inner := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(inner, e.filters(p, q)))
}

func TestSQLPushSelectIntoJoinLeft(t *testing.T) {
	expectSQLRewrites(t, "PushSelectIntoJoinLeft", "SELECT * FROM (VALUES (1)) AS l(a) LEFT JOIN (VALUES (1)) AS r(b) ON a=b WHERE a>0 AND a<>b")
}
