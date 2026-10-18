package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPropagateEmptyRelation(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PropagateEmptyRelation")
	_, leftCols := e.scan("u", 2)
	right, rightCols := e.scan("t", 2)
	left := e.empty(leftCols...)
	before := e.f.ConstructInnerJoin(left, right, nil, &memo.JoinPrivate{})
	wantCols := append(append(opt.ColList{}, leftCols...), rightCols...)
	e.expectRewrite(rule, before, e.empty(wantCols...))

	nonEmptyLeft, nonEmptyCols := e.scan("u", 2)
	nearMiss := e.f.ConstructInnerJoin(nonEmptyLeft, right, nil, &memo.JoinPrivate{})
	if nonEmptyCols[0] == leftCols[0] {
		t.Fatal("expected independently allocated left columns")
	}
	e.expectNoMatch(rule, nearMiss)
}
