package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushSelectIntoProjectSet(t *testing.T) {
	rule := loadRule(t, "PushSelectIntoProjectSet")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	join := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	p := e.f.ConstructGt(e.v(lc[0]), e.num(0))
	q := e.f.ConstructLt(e.v(rc[0]), e.num(10))
	before := e.f.ConstructSelect(join, e.filters(p, q))
	filtered := e.f.ConstructSelect(l, e.filters(p))
	want := e.f.ConstructSelect(e.f.ConstructInnerJoin(filtered, r, on, &memo.JoinPrivate{}), e.filters(q))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(q, p)))
	leftJoin := e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(leftJoin, e.filters(p, q)))
}

func TestSQLPushSelectIntoProjectSet(t *testing.T) {
	expectSQLRewrites(t, "PushSelectIntoProjectSet", "SELECT * FROM (VALUES (1)) AS l(a) JOIN (VALUES (1)) AS r(b) ON a=b WHERE a>0 AND b<10")
}
