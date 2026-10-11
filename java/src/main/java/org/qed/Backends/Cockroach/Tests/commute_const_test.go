package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestCommuteConst(t *testing.T) {
	rule := loadRule(t, "CommuteConst")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	join := e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{})
	before := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))))
	want := e.f.ConstructSelect(join, e.filters(e.f.ConstructEq(e.v(rc[0]), e.v(lc[0]))))
	e.expectRewrite(rule, before, want)

	e.expectNoMatch(rule, e.f.ConstructSelect(join, e.filters(e.f.ConstructNe(e.v(lc[0]), e.v(rc[0])))))
	withOn := e.f.ConstructInnerJoin(l, r, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))), &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructSelect(withOn, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))))
}

func TestSQLCommuteConst(t *testing.T) {
	expectSQLRewrites(t, "CommuteConst", "SELECT a, b FROM (VALUES (1)) AS l(a), (VALUES (2)) AS r(b) WHERE a = b")
}
