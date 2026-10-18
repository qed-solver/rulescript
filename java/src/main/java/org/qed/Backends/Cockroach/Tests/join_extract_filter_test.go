package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestJoinExtractFilter(t *testing.T) {
	rule := loadRule(t, "JoinExtractFilter")
	e := newEnv(t)
	l, lc := e.scan("u", 1)
	r, rc := e.scan("t", 1)
	on := e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])))
	before := e.f.ConstructInnerJoin(l, r, on, &memo.JoinPrivate{})
	want := e.f.ConstructSelect(e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}), on)
	e.expectRewrite(rule, before, want)

	twoConds := e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])),
		e.f.ConstructGt(e.v(rc[0]), e.num(3)),
	)
	beforeTwo := e.f.ConstructInnerJoin(l, r, twoConds, &memo.JoinPrivate{})
	wantTwo := e.f.ConstructSelect(e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}), twoConds)
	e.expectRewrite(rule, beforeTwo, wantTwo)

	e.expectNoMatch(rule, e.f.ConstructLeftJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSemiJoin(l, r, on, &memo.JoinPrivate{}))
	e.expectNoMatch(rule, e.f.ConstructSelect(e.f.ConstructInnerJoin(l, r, nil, &memo.JoinPrivate{}), on))
}
