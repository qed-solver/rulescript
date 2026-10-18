package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushFilterIntoJoinLeftAndRight(t *testing.T) {
	rule := loadRule(t, "PushFilterIntoJoinLeftAndRight")
	e := newEnv(t)
	left, lc := e.scan("nn", 2)
	right, rc := e.scan("nn", 2)
	before := e.f.ConstructInnerJoin(left, right, e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])),
		e.f.ConstructEq(e.v(lc[1]), e.v(rc[1])),
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[1])),
	), &memo.JoinPrivate{})
	wantLeft := e.f.ConstructSelect(left, e.filters(e.f.ConstructEq(e.v(lc[0]), e.v(lc[1]))))
	wantRight := e.f.ConstructSelect(right, e.filters(e.f.ConstructEq(e.v(rc[0]), e.v(rc[1]))))
	want := e.f.ConstructInnerJoin(wantLeft, wantRight, e.filters(
		e.f.ConstructEq(e.v(lc[0]), e.v(rc[0])),
		e.f.ConstructEq(e.v(lc[1]), e.v(rc[1])),
	), &memo.JoinPrivate{})
	e.expectRewrite(rule, before, want)

	nearLeft, nlc := e.scan("nn", 2)
	nearRight, nrc := e.scan("nn", 2)
	nearMiss := e.f.ConstructInnerJoin(nearLeft, nearRight, e.filters(
		e.f.ConstructEq(e.v(nlc[0]), e.v(nrc[0])),
		e.f.ConstructEq(e.v(nlc[1]), e.v(nrc[1])),
	), &memo.JoinPrivate{})
	e.expectNoMatch(rule, nearMiss)
}
