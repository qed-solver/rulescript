package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPushPredicateThroughJoin(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PushPredicateThroughJoin")

	left, lc := e.scan("u", 1)
	right, rc := e.scan("t", 1)
	leftCondition := e.f.ConstructEq(e.v(lc[0]), e.num(1))
	rightCondition := e.f.ConstructEq(e.v(rc[0]), e.num(2))
	commonCondition := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	joinCondition := e.f.ConstructEq(e.v(lc[0]), e.v(rc[0]))
	before := e.f.ConstructSelect(
		e.f.ConstructInnerJoin(left, right, e.filters(joinCondition), &memo.JoinPrivate{}),
		e.filters(leftCondition, rightCondition, commonCondition),
	)
	want := e.f.ConstructInnerJoin(
		e.f.ConstructSelect(left, e.filters(leftCondition)),
		e.f.ConstructSelect(right, e.filters(rightCondition)),
		e.filters(joinCondition, commonCondition),
		&memo.JoinPrivate{},
	)
	e.expectRewrite(rule, before, want)

	nearMissLeft, nlc := e.scan("u", 1)
	nearMissRight, nrc := e.scan("t", 1)
	nearMiss := e.f.ConstructSelect(
		e.f.ConstructInnerJoin(nearMissLeft, nearMissRight,
			e.filters(e.f.ConstructEq(e.v(nlc[0]), e.v(nrc[0]))), &memo.JoinPrivate{}),
		e.filters(
			e.f.ConstructEq(e.v(nlc[0]), e.num(1)),
			e.f.ConstructEq(e.v(nrc[0]), e.num(2)),
			e.f.ConstructEq(e.v(nlc[0]), e.v(nrc[0])),
			e.f.ConstructEq(e.v(nlc[0]), e.num(3)),
		),
	)
	e.expectNoMatch(rule, nearMiss)
}
