package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestTryDecorrelateProjectSet(t *testing.T) {
	rule := loadRule(t, "TryDecorrelateProjectSet")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	i, ic := e.scan("t", 1)
	emit, ec := e.scan("nn", 1)
	membership := e.filters(e.f.ConstructEq(e.v(ic[0]), e.v(ec[0])))
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(ic[0])))
	projectSet := e.f.ConstructInnerJoin(i, emit, membership, &memo.JoinPrivate{})
	before := e.f.ConstructInnerJoin(l, projectSet, on, &memo.JoinPrivate{})
	cross := e.f.ConstructInnerJoin(l, i, nil, &memo.JoinPrivate{})
	want := e.f.ConstructSelect(e.f.ConstructInnerJoin(cross, emit, membership, &memo.JoinPrivate{}), on)
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, projectSet, on, &memo.JoinPrivate{}))
	wrongInner := e.f.ConstructLeftJoin(i, emit, membership, &memo.JoinPrivate{})
	e.expectNoMatch(rule, e.f.ConstructInnerJoin(l, wrongInner, on, &memo.JoinPrivate{}))
}

func TestSQLTryDecorrelateProjectSet(t *testing.T) {
	expectSQLFires(t, "TryDecorrelateProjectSet", "SELECT * FROM (VALUES (1)) AS l(a) JOIN ((VALUES (1)) AS i(b) JOIN (VALUES (1)) AS r(c) ON b=c) ON a=b")
}
